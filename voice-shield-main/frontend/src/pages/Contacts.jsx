import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { RefreshCw, Check, Plus, X, Trash2, ShieldAlert } from 'lucide-react';
import api from '../services/api';
import './Contacts.css';

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || '846997243859-0jjs99qi5odj4e98ckndf2rtrvp1u04o.apps.googleusercontent.com';

/**
 * Loads the Google Identity Services (GIS) script dynamically.
 * Returns a promise that resolves when the script is loaded.
 */
function loadGoogleIdentityScript() {
  return new Promise((resolve, reject) => {
    if (window.google?.accounts?.oauth2) {
      resolve();
      return;
    }
    const existing = document.querySelector('script[src="https://accounts.google.com/gsi/client"]');
    if (existing) {
      existing.addEventListener('load', resolve);
      existing.addEventListener('error', reject);
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = resolve;
    script.onerror = reject;
    document.head.appendChild(script);
  });
}

function Contacts({ currentUser }) {
  const navigate = useNavigate();
  const [contacts, setContacts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showAddModal, setShowAddModal] = useState(false);
  
  // Sync state
  const [syncing, setSyncing] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  
  // New contact form inputs
  const [newName, setNewName] = useState('');
  const [newPhone, setNewPhone] = useState('');
  const [newRelation, setNewRelation] = useState('Family');

  // Google Identity Services token client ref
  const tokenClientRef = useRef(null);
  const [gisLoaded, setGisLoaded] = useState(false);

  const userId = currentUser?.id || 'default_user';

  // Load Google Identity Services script on mount
  useEffect(() => {
    loadGoogleIdentityScript()
      .then(() => setGisLoaded(true))
      .catch((err) => console.warn('Failed to load Google Identity Services:', err));
  }, []);

  // Load contacts on mount
  useEffect(() => {
    fetchContacts();
  }, [userId]);

  const fetchContacts = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const data = await api.getContacts(userId);
      setContacts(data);
    } catch (err) {
      console.warn('Backend contacts endpoint failed, loading from local cache:', err.message);
      // Fallback local storage
      const cached = localStorage.getItem(`voiceshield_contacts_${userId}`);
      if (cached) {
        setContacts(JSON.parse(cached));
      } else {
        setContacts([]);
      }
    } finally {
      setLoading(false);
    }
  };

  const saveContactsCache = (updatedContacts) => {
    localStorage.setItem(`voiceshield_contacts_${userId}`, JSON.stringify(updatedContacts));
  };

  const handleCall = (phone) => {
    navigate(`/calls?phone=${encodeURIComponent(phone)}`);
  };

  const handleDeleteContact = async (contactId) => {
    setErrorMsg('');
    try {
      await api.deleteContact(contactId);
      const updated = contacts.filter(c => c.id !== contactId);
      setContacts(updated);
      saveContactsCache(updated);
      setSuccessMsg('Contact removed successfully.');
      setTimeout(() => setSuccessMsg(''), 3000);
    } catch (err) {
      console.warn('Backend delete contact failed, updating local cache only:', err.message);
      const updated = contacts.filter(c => c.id !== contactId);
      setContacts(updated);
      saveContactsCache(updated);
      setSuccessMsg('Contact removed successfully (local).');
      setTimeout(() => setSuccessMsg(''), 3000);
    }
  };

  const handleAddContactSubmit = async (e) => {
    e.preventDefault();
    if (!newName || !newPhone) return;
    setErrorMsg('');

    const contactData = {
      name: newName,
      phone: newPhone,
      relation: newRelation
    };

    try {
      const saved = await api.addContact(userId, newName, newPhone, newRelation);
      const updated = [...contacts, saved];
      setContacts(updated);
      saveContactsCache(updated);
      setSuccessMsg(`Contact "${newName}" added successfully.`);
      setTimeout(() => setSuccessMsg(''), 4000);
    } catch (err) {
      console.warn('Backend add contact failed, saving to local cache only:', err.message);
      const localSaved = {
        id: `local_${Date.now()}`,
        ...contactData,
        created_at: new Date().toISOString()
      };
      const updated = [...contacts, localSaved];
      setContacts(updated);
      saveContactsCache(updated);
      setSuccessMsg(`Contact "${newName}" added successfully (local).`);
      setTimeout(() => setSuccessMsg(''), 4000);
    }

    setNewName('');
    setNewPhone('');
    setNewRelation('Family');
    setShowAddModal(false);
  };

  /**
   * Handles the real Google OAuth token response.
   * Sends the access_token to the backend to fetch real Google contacts.
   */
  const handleGoogleTokenResponse = useCallback(async (tokenResponse) => {
    if (tokenResponse.error) {
      setErrorMsg(`Google authorization failed: ${tokenResponse.error}`);
      setSyncing(false);
      return;
    }

    const accessToken = tokenResponse.access_token;
    if (!accessToken) {
      setErrorMsg('No access token received from Google.');
      setSyncing(false);
      return;
    }

    // Get the user's Google email from the token info
    let googleEmail = currentUser?.email || '';
    try {
      const userInfoRes = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (userInfoRes.ok) {
        const userInfo = await userInfoRes.json();
        googleEmail = userInfo.email || googleEmail;
      }
    } catch (e) {
      // Use fallback email
    }

    // Now call the backend with the REAL access token
    try {
      const response = await api.syncGoogleContacts(userId, googleEmail, accessToken);
      const synced = response.contacts || [];
      
      // Merge with existing contacts, dedup by phone
      const existingPhones = new Set(contacts.map(c => c.phone));
      const newContacts = synced.filter(c => !existingPhones.has(c.phone));
      const merged = [...contacts, ...newContacts];
      
      setContacts(merged);
      saveContactsCache(merged);
      setSuccessMsg(response.message || `Synced ${newContacts.length} new contacts from Google.`);
      setTimeout(() => setSuccessMsg(''), 5000);
    } catch (err) {
      console.error('Google contacts sync failed:', err);
      setErrorMsg(err.response?.data?.detail || err.message || 'Failed to sync Google contacts. Please try again.');
      setTimeout(() => setErrorMsg(''), 6000);
    } finally {
      setSyncing(false);
    }
  }, [contacts, currentUser, userId]);

  /**
   * Triggers the real Google OAuth flow using Google Identity Services.
   * Opens Google's consent popup asking for contacts.readonly scope.
   */
  const handleSyncGoogleContacts = useCallback(() => {
    if (!gisLoaded || !window.google?.accounts?.oauth2) {
      setErrorMsg('Google Identity Services not loaded yet. Please try again in a moment.');
      return;
    }

    setSyncing(true);
    setErrorMsg('');
    setSuccessMsg('');

    try {
      // Initialize or reuse the token client
      if (!tokenClientRef.current) {
        tokenClientRef.current = window.google.accounts.oauth2.initTokenClient({
          client_id: GOOGLE_CLIENT_ID,
          scope: 'https://www.googleapis.com/auth/contacts.readonly https://www.googleapis.com/auth/userinfo.email',
          callback: handleGoogleTokenResponse,
          error_callback: (err) => {
            console.error('Google OAuth error:', err);
            setErrorMsg('Google sign-in was cancelled or failed. Please try again.');
            setSyncing(false);
          },
        });
      }
      
      // Request the access token — this opens Google's consent popup
      tokenClientRef.current.requestAccessToken({ prompt: 'consent' });
    } catch (err) {
      console.error('Failed to initiate Google OAuth:', err);
      setErrorMsg('Failed to start Google sign-in. Please check your connection and try again.');
      setSyncing(false);
    }
  }, [gisLoaded, handleGoogleTokenResponse]);

  return (
    <section className="contacts-page-container">
      <div className="contacts-header-row">
        <div className="header-title-section">
          <span className="contacts-subtitle">TRUST LAYER</span>
          <h2 className="contacts-title">Trusted contacts</h2>
          <p className="contacts-desc">People you trust get an extra verification path.</p>
        </div>
        <div className="header-actions-section">
          <button 
            className="sync-contacts-btn" 
            onClick={handleSyncGoogleContacts}
            disabled={syncing}
          >
            <RefreshCw size={14} className={syncing ? 'spin' : ''} />
            <span>{syncing ? 'Syncing...' : 'Sync Google Contacts'}</span>
          </button>
          <button className="add-contact-btn" onClick={() => setShowAddModal(true)}>
            <Plus size={16} />
            <span>Add contact</span>
          </button>
        </div>
      </div>

      {successMsg && <div className="toast-success-msg">{successMsg}</div>}
      {errorMsg && <div className="toast-error-msg">{errorMsg}</div>}

      {loading ? (
        <div className="contacts-loading">Loading contacts...</div>
      ) : contacts.length === 0 ? (
        <div className="no-contacts-card">
          <ShieldAlert size={36} className="no-contacts-icon" />
          <h3>No Trusted Contacts</h3>
          <p>Add contacts manually or sync with Google Contacts to build your security registry.</p>
        </div>
      ) : (
        <div className="contacts-grid-list">
          {contacts.map((contact) => (
            <div key={contact.id} className="contact-premium-card" onClick={() => handleCall(contact.phone)}>
              <div className="card-left">
                <div className="contact-avatar-wrapper">
                  <span className="avatar-initials">
                    {contact.name ? contact.name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : 'C'}
                  </span>
                  <div className="avatar-check-badge">
                    <Check size={10} strokeWidth={3} />
                  </div>
                </div>
                <div className="contact-details-group">
                  <span className="contact-full-name">{contact.name}</span>
                  <span className="contact-relation-tag">{contact.relation || 'Friend'}</span>
                </div>
              </div>
              <div className="card-right">
                <span className="trusted-badge-pill">TRUSTED</span>
                <button 
                  className="delete-contact-btn" 
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDeleteContact(contact.id);
                  }}
                  title="Remove from Trusted List"
                >
                  <Trash2 size={15} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add Contact Modal Dialog */}
      {showAddModal && (
        <div className="modal-overlay">
          <div className="modal-content-card">
            <div className="modal-header">
              <h3>Add Trusted Contact</h3>
              <button className="modal-close-btn" onClick={() => setShowAddModal(false)}>
                <X size={18} />
              </button>
            </div>
            <form onSubmit={handleAddContactSubmit} className="modal-form">
              <div className="modal-form-group">
                <label>Full Name</label>
                <input 
                  type="text" 
                  placeholder="e.g. Aman Verma" 
                  value={newName} 
                  onChange={(e) => setNewName(e.target.value)} 
                  required
                />
              </div>
              <div className="modal-form-group">
                <label>Mobile Number</label>
                <input 
                  type="tel" 
                  placeholder="e.g. +91 99999 55504" 
                  value={newPhone} 
                  onChange={(e) => setNewPhone(e.target.value)} 
                  required
                />
              </div>
              <div className="modal-form-group">
                <label>Relationship Category</label>
                <select value={newRelation} onChange={(e) => setNewRelation(e.target.value)}>
                  <option value="Family">Family</option>
                  <option value="Friend">Friend</option>
                  <option value="Doctor">Doctor</option>
                  <option value="Work">Work</option>
                </select>
              </div>
              <button type="submit" className="modal-submit-btn">Save Contact</button>
            </form>
          </div>
        </div>
      )}
    </section>
  );
}

export default Contacts;
