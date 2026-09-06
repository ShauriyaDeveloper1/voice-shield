import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Phone } from 'lucide-react';
import { useCall } from '../context/CallContext';
import './CallHistory.css';

const MOCK_HISTORY = [
  { id: '1', name: 'Rahul Kumar', phone: '+91 98765 43210', time: 'Today, 16:42', risk: 'low', score: 18, status: 'Safe' },
  { id: '2', name: 'Unknown caller', phone: '+91 70000 99903', time: 'Today, 14:18', risk: 'high', score: 91, status: 'Blocked' },
  { id: '3', name: 'Priya Sharma', phone: '+91 88888 11117', time: 'Yesterday, 19:06', risk: 'low', score: 8, status: 'Safe' },
  { id: '4', name: 'Bank Support', phone: '+91 80000 22221', time: 'Yesterday, 11:31', risk: 'medium', score: 57, status: 'Warned' },
  { id: '5', name: 'Aman Verma', phone: '+91 99999 55504', time: 'Aug 24, 21:10', risk: 'low', score: 12, status: 'Safe' }
];

function CallHistory() {
  const navigate = useNavigate();
  const { initiateCall } = useCall();
  const [searchTerm, setSearchTerm] = useState('');
  const [riskFilter, setRiskFilter] = useState('all');
  const [history, setHistory] = useState(MOCK_HISTORY);

  // Load from local storage if any calls were recorded during this session
  useEffect(() => {
    const localCalls = localStorage.getItem('voiceshield_call_history');
    if (localCalls) {
      try {
        const parsed = JSON.parse(localCalls);
        // Combine mock data with real local calls, putting local calls at the top
        const combined = [...parsed, ...MOCK_HISTORY];
        // Remove duplicates by ID just in case
        const unique = combined.filter((c, index, self) =>
          self.findIndex(t => t.id === c.id) === index
        );
        setHistory(unique);
      } catch (err) {
        console.error(err);
      }
    }
  }, []);

  const filteredHistory = history.filter(item => {
    const matchesSearch = 
      item.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.phone.includes(searchTerm);
    
    const matchesRisk = 
      riskFilter === 'all' || 
      item.risk === riskFilter;

    return matchesSearch && matchesRisk;
  });

  const handleEstablishCall = (item) => {
    const phone = item.phone?.trim();
    if (!phone || phone.toLowerCase() === 'unknown') {
      initiateCall('000', item.name || 'Demo Session');
    } else {
      initiateCall(phone, item.name);
    }
    navigate('/calls');
  };

  return (
    <section className="history-page-container">
      <div className="history-card-panel">
        <div className="history-header-row">
          <div className="header-text-group">
            <span className="history-subtitle">CALL INTELLIGENCE</span>
            <h3 className="history-title">Call history</h3>
          </div>
          
          <div className="history-filters-group">
            <div className="search-bar-wrapper">
              <Search size={14} className="search-bar-icon" />
              <input 
                type="text" 
                className="search-input-field" 
                placeholder="Search calls..." 
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>

            <select 
              className="risk-filter-dropdown"
              value={riskFilter}
              onChange={(e) => setRiskFilter(e.target.value)}
            >
              <option value="all">All risks</option>
              <option value="low">Low Risk</option>
              <option value="medium">Medium Risk</option>
              <option value="high">High Risk</option>
            </select>
          </div>
        </div>

        <div className="table-responsive-container">
          <table className="history-data-table">
            <thead>
              <tr>
                <th className="th-left">CALLER</th>
                <th>TIME</th>
                <th>RISK</th>
                <th>SCORE</th>
                <th>STATUS</th>
              </tr>
            </thead>
            <tbody>
              {filteredHistory.length > 0 ? (
                filteredHistory.map((item) => (
                  <tr key={item.id} className="history-row-item">
                    <td className="td-caller td-left">
                      <div className="caller-name-row">
                        <span className="caller-name-span">{item.name}</span>
                        <button
                          type="button"
                          className="call-again-btn"
                          onClick={() => handleEstablishCall(item)}
                          title={`Call ${item.name} again`}
                        >
                          <Phone size={13} className="call-again-icon" />
                        </button>
                      </div>
                      <span className="caller-phone-span">{item.phone}</span>
                    </td>
                    <td className="td-time">{item.time}</td>
                    <td className="td-risk">
                      <span className={`badge ${item.risk}`}>
                        {item.risk}
                      </span>
                    </td>
                    <td className="td-score">{item.score}</td>
                    <td className={`td-status text-${item.status.toLowerCase()}`}>{item.status}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="5" className="empty-table-row">
                    No matching calls found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

export default CallHistory;
