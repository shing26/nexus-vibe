import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import Skeleton from '../components/Skeleton';
import { Mail } from 'lucide-react';

interface Message {
  /** Long on the server; see JacksonConfig's ToStringSerializer */
  id: string;
  fromUserName: string;
  content: string;
  isRead: number;
  createTime: string;
}

export default function MessagesPage() {
  const { t } = useTranslation('messages');
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
        setError(t('failedToLoad'));
      } finally {
        setLoading(false);
      }
    })();
  }, [isAuthenticated, t]);

  const markAsRead = async (messageId: string) => {
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
      setError(t('failedMarkAll'));
    }
  };

  if (!isAuthenticated) return null;

  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <div className="mb-8 flex items-center justify-between">
        <h1 className="text-lg font-semibold text-vibe-text">{t('title')}</h1>
        {messages.length > 0 && (
          <button
            type="button"
            onClick={markAllAsRead}
            className="rounded-md border border-vibe-border px-3 py-1.5 text-xs text-vibe-muted transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent"
          >
            {t('markAllRead')}
          </button>
        )}
      </div>

      {error && (
        <div role="alert" className="mb-6 rounded-md border border-vibe-danger/40 bg-vibe-danger/5 px-4 py-3 text-xs text-vibe-danger">
          {error}
        </div>
      )}

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="space-y-2 rounded-lg border border-vibe-border bg-vibe-surface p-4">
              <Skeleton className="h-4 w-1/4" />
              <Skeleton className="h-3 w-full" />
              <Skeleton className="h-3 w-2/3" />
            </div>
          ))}
        </div>
      ) : messages.length === 0 ? (
        <div className="py-16 text-center">
          <div className="mb-4 inline-flex h-16 w-16 items-center justify-center rounded-full bg-vibe-card">
            <Mail className="h-7 w-7 text-vibe-faint" />
          </div>
          <p className="text-sm text-vibe-muted">{t('emptyTitle')}</p>
          <p className="mt-1 text-xs text-vibe-faint">{t('emptyDesc')}</p>
        </div>
      ) : (
        <div className="space-y-2">
          {messages.map((msg) => {
            const isUnread = msg.isRead === 0;
            const time = new Date(msg.createTime).toLocaleString();
            return (
              <button
                key={msg.id}
                type="button"
                onClick={() => isUnread && markAsRead(msg.id)}
                className={`w-full rounded-lg border bg-vibe-surface p-4 text-left transition-colors ${
                  isUnread
                    ? 'border-vibe-accent/40 hover:border-vibe-accent/60 cursor-pointer'
                    : 'border-vibe-border cursor-default'
                }`}
              >
                <div className="flex items-start gap-3">
                  {/* Read/unread indicator */}
                  <div className="mt-1.5 shrink-0">
                    {isUnread ? (
                      <div className="h-2.5 w-2.5 rounded-full bg-vibe-accent" />
                    ) : (
                      <div className="h-2.5 w-2.5 rounded-full border border-vibe-border" />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <span className={`text-xs ${isUnread ? 'font-semibold text-vibe-text' : 'text-vibe-muted'}`}>
                        {msg.fromUserName || t('system')}
                      </span>
                      <span className="shrink-0 text-xs tabular-nums text-vibe-faint">{time}</span>
                    </div>
                    <p className={`mt-1 text-sm ${isUnread ? 'text-vibe-text' : 'text-vibe-muted'}`}>
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
