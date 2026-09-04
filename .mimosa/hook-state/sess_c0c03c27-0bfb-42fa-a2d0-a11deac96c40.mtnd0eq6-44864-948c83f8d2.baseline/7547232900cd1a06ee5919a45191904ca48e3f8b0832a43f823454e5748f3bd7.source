import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';

interface Message {
  id: number;
  fromUserName: string;
  content: string;
  isRead: number;
  createTime: string;
}

export default function MessagesPage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();

  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    if (!isAuthenticated) return;
    (async () => {
      try {
        const res = await apiClient.get('/messages');
        setMessages(res.data.data || []);
      } catch {
        setError('Failed to load messages.');
      } finally {
        setLoading(false);
      }
    })();
  }, [isAuthenticated]);

  const markAsRead = async (messageId: number) => {
    // Optimistically update UI
    setMessages((prev) =>
      prev.map((m) =>
        m.id === messageId && m.isRead === 0
          ? { ...m, isRead: 1 }
          : m
      )
    );
    try {
      await apiClient.post(`/messages/${messageId}/read`);
    } catch {
      // Revert on failure
      setMessages((prev) =>
        prev.map((m) =>
          m.id === messageId ? { ...m, isRead: 0 } : m
        )
      );
    }
  };

  const markAllAsRead = async () => {
    try {
      await apiClient.post('/messages/read-all');
      setMessages((prev) => prev.map((m) => ({ ...m, isRead: 1 })));
    } catch {
      setError('Failed to mark all messages as read.');
    }
  };

  if (!isAuthenticated) return null;

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-base font-semibold font-mono text-slate-100"><span className="text-vibe-cyan">$</span> Messages</h1>
        {messages.length > 0 && (
          <button
            onClick={markAllAsRead}
            className="px-3 py-1.5 rounded-lg border border-vibe-border text-[11px] font-mono text-slate-400 hover:text-vibe-cyan hover:border-vibe-cyan/40 transition-colors"
          >
            Mark all read
          </button>
        )}
      </div>

      {error && (
        <div className="bg-red-900/30 border border-red-500/40 text-red-400 px-4 py-3 rounded-lg text-[11px] font-mono mb-6">
          {error}
        </div>
      )}

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="animate-pulse bg-vibe-surface border border-vibe-border rounded-lg p-4 space-y-2">
              <div className="h-4 bg-vibe-card rounded w-1/4" />
              <div className="h-3 bg-vibe-card rounded w-full" />
              <div className="h-3 bg-vibe-card rounded w-2/3" />
            </div>
          ))}
        </div>
      ) : messages.length === 0 ? (
        <div className="text-center py-16">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-vibe-card rounded-full mb-4">
            <svg className="w-8 h-8 text-slate-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
          </div>
          <p className="text-slate-500 text-sm font-mono">No messages yet.</p>
          <p className="text-xs text-slate-600 mt-1 font-mono">System notifications will appear here.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {messages.map((msg) => {
            const isUnread = msg.isRead === 0;
            const time = new Date(msg.createTime).toLocaleString();
            return (
                <button
                  key={msg.id}
                  onClick={() => isUnread && markAsRead(msg.id)}
                  className={`w-full text-left bg-vibe-surface border rounded-lg p-4 transition-colors ${
                    isUnread
                      ? 'border-vibe-cyan/30 hover:border-vibe-cyan/50 cursor-pointer'
                      : 'border-vibe-border cursor-default'
                  }`}
                >
                  <div className="flex items-start gap-3">
                    {/* Read/unread indicator */}
                    <div className="mt-1.5 shrink-0">
                      {isUnread ? (
                        <div className="w-2.5 h-2.5 rounded-full bg-vibe-cyan" />
                      ) : (
                        <div className="w-2.5 h-2.5 rounded-full border border-slate-600" />
                      )}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center justify-between gap-2">
                        <span className={`text-xs font-mono ${isUnread ? 'text-slate-200 font-semibold' : 'text-slate-500'}`}>
                          {msg.fromUserName || 'System'}
                        </span>
                        <span className="text-[10px] text-slate-600 shrink-0 font-mono">{time}</span>
                      </div>
                      <p className={`mt-1 text-xs font-mono ${isUnread ? 'text-slate-300' : 'text-slate-500'}`}>
                        {msg.content}
                      </p>
                    </div>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
