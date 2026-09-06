import axios from 'axios';

export let API_BASE = import.meta.env.VITE_API_BASE || import.meta.env.VITE_API_URL || 'http://127.0.0.1:8000';
if (API_BASE.endsWith('/')) {
  API_BASE = API_BASE.slice(0, -1);
}

const client = axios.create({
  baseURL: API_BASE,
  timeout: 20000,
});

/**
 * Checks the status of the backend API.
 */
export async function checkHealth() {
  try {
    const response = await client.get('/health');
    return response.data?.status === 'ok';
  } catch (error) {
    console.error('Backend health check failed:', error.message);
    return false;
  }
}

/**
 * Initiates a new call session.
 */
export async function createCall(callerId, receiverId) {
  try {
    const response = await client.post('/api/calls/', {
      caller_id: callerId,
      receiver_id: receiverId,
      status: 'started',
    });
    return response.data;
  } catch (error) {
    console.error('Failed to create call:', error.message);
    throw error;
  }
}

/**
 * Submits analytical feature results for an active call.
 */
export async function analyzeCall(callId, features) {
  try {
    const response = await client.post(`/api/calls/${callId}/analysis`, {
      call_id: callId,
      deepfake_score: features.deepfakeScore ?? 0.15,
      speaker_similarity: features.speakerSimilarity ?? 0.90,
      prosody_score: features.prosodyScore ?? 0.12,
      context_score: features.contextScore ?? 0.20,
    });
    return response.data;
  } catch (error) {
    console.error('Failed to analyze call:', error.message);
    throw error;
  }
}

/**
 * Uploads an audio file for offline deepfake/risk analysis.
 */
export async function uploadAudioFile(file) {
  try {
    const formData = new FormData();
    formData.append('file', file);
    const response = await client.post('/api/analysis/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  } catch (error) {
    console.error('Failed to upload audio file:', error.message);
    throw error;
  }
}

/**
 * Registers a new user.
 */
export async function registerUser(name, email, phone, password) {
  try {
    const response = await client.post('/api/auth/register', {
      name,
      email,
      phone,
      password,
    });
    return response.data;
  } catch (error) {
    let errMsg = 'Registration failed';
    if (error.response?.data?.detail) {
      const detail = error.response.data.detail;
      if (typeof detail === 'string') {
        errMsg = detail;
      } else if (Array.isArray(detail)) {
        errMsg = detail.map(d => `${d.loc.join('.')}: ${d.msg}`).join(', ');
      } else {
        errMsg = JSON.stringify(detail);
      }
    } else if (error.message) {
      errMsg = error.message;
    }
    console.error('Registration failed:', errMsg);
    throw new Error(errMsg);
  }
}

/**
 * Logins a user using name or email, and password.
 */
export async function loginUser(username, password) {
  try {
    const response = await client.post('/api/auth/login', {
      username,
      password,
    });
    return response.data;
  } catch (error) {
    let errMsg = 'Login failed';
    if (error.response?.data?.detail) {
      const detail = error.response.data.detail;
      if (typeof detail === 'string') {
        errMsg = detail;
      } else if (Array.isArray(detail)) {
        errMsg = detail.map(d => `${d.loc.join('.')}: ${d.msg}`).join(', ');
      } else {
        errMsg = JSON.stringify(detail);
      }
    } else if (error.message) {
      errMsg = error.message;
    }
    console.error('Login failed:', errMsg);
    throw new Error(errMsg);
  }
}

/**
 * Updates user profile details.
 */
export async function updateUserProfile(userId, name, email, phone) {
  try {
    const response = await client.put(`/api/users/${userId}`, {
      name,
      email,
      phone,
    });
    return response.data;
  } catch (error) {
    let errMsg = 'Failed to update profile';
    if (error.response?.data?.detail) {
      errMsg = error.response.data.detail;
    }
    console.error('Update profile failed:', errMsg);
    throw new Error(errMsg);
  }
}

/**
 * Fetches user-specific trusted contacts.
 */
export async function getContacts(userId) {
  try {
    const response = await client.get(`/api/contacts/?user_id=${userId}`);
    return response.data;
  } catch (error) {
    console.error('Failed to get contacts:', error.message);
    throw error;
  }
}

/**
 * Adds a trusted contact.
 */
export async function addContact(userId, name, phone, relation) {
  try {
    const response = await client.post(`/api/contacts/?user_id=${userId}`, {
      name,
      phone,
      relation,
    });
    return response.data;
  } catch (error) {
    console.error('Failed to add contact:', error.message);
    throw error;
  }
}

/**
 * Deletes a trusted contact by ID.
 */
export async function deleteContact(contactId) {
  try {
    const response = await client.delete(`/api/contacts/${contactId}`);
    return response.data;
  } catch (error) {
    console.error('Failed to delete contact:', error.message);
    throw error;
  }
}

/**
 * Synchronizes contacts with Google.
 */
export async function syncGoogleContacts(userId, googleEmail, token) {
  try {
    const response = await client.post(`/api/contacts/sync-google?user_id=${userId}`, {
      google_email: googleEmail,
      token: token,
    });
    return response.data;
  } catch (error) {
    console.error('Failed to sync Google contacts:', error.message);
    throw error;
  }
}

/**
 * Retrieves alerts/warnings for a specific user.
 */
export async function getUserAlerts(userId) {
  try {
    const response = await client.get(`/api/users/${userId}/alerts`);
    return response.data;
  } catch (error) {
    console.error('Failed to get user alerts:', error.message);
    throw error;
  }
}

/**
 * Sends email verification via Supabase before registration.
 */
export async function sendEmailVerification(email, name = '', phone = '', redirectTo = '') {
  try {
    const response = await client.post('/api/auth/send-verification', {
      email,
      name,
      phone,
      redirect_to: redirectTo,
    });
    return response.data;
  } catch (error) {
    let errMsg = 'Failed to send verification email';
    if (error.response?.data?.detail) {
      errMsg = error.response.data.detail;
    } else if (error.message) {
      errMsg = error.message;
    }
    console.error('Send verification email failed:', errMsg);
    throw new Error(errMsg);
  }
}

/**
 * Confirms or initializes user profile upon email verification callback.
 */
export async function confirmVerifiedProfile(id, email, name = '', phone = '') {
  try {
    const response = await client.post('/api/auth/confirm-profile', {
      id,
      email,
      name,
      phone,
    });
    return response.data;
  } catch (error) {
    console.warn('Failed to confirm profile via API:', error.message);
    return null;
  }
}

/**
 * Verifies a Google OAuth session with the backend and creates/returns user profile.
 */
export async function googleSignIn(accessToken, providerToken = null) {
  try {
    const response = await client.post('/api/auth/google', {
      access_token: accessToken,
      provider_token: providerToken,
    });
    return response.data;
  } catch (error) {
    let errMsg = 'Google sign-in failed';
    if (error.response?.data?.detail) {
      errMsg = error.response.data.detail;
    } else if (error.message) {
      errMsg = error.message;
    }
    console.error('Google sign-in failed:', errMsg);
    throw new Error(errMsg);
  }
}

export default {
  checkHealth,
  createCall,
  analyzeCall,
  uploadAudioFile,
  registerUser,
  loginUser,
  updateUserProfile,
  getContacts,
  addContact,
  deleteContact,
  syncGoogleContacts,
  getUserAlerts,
  sendEmailVerification,
  confirmVerifiedProfile,
  googleSignIn,
};
