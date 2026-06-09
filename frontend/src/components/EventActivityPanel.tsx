import { useMemo, useState, useEffect } from 'react';
import { fetchParticipantTimeline, type LiveEventSnapshot, type TimelineEntry } from '../api/liveEventApi';
import { useUserActivityStream } from '../hooks/useUserActivityStream';

interface EventActivityPanelProps {
  snapshot: LiveEventSnapshot;
  participantId: string | null;
  selectedParticipantId?: string | null;
  onSelectParticipant?: (id: string) => void;
  apiBaseUrl?: string;
}

export function EventActivityPanel({
  snapshot,
  participantId,
  selectedParticipantId,
  apiBaseUrl,
}: EventActivityPanelProps) {
  // Determine which participant to inspect (defaults to self if nothing selected or self is selected)
  const targetId = selectedParticipantId ?? participantId;
  const isMe = targetId === participantId;

  const targetParticipant = snapshot.participants.find((p) => p.id === targetId) ?? null;

  // Stream activity logs if it's an AI participant (or if explicitly selected)
  const { activities: liveActivities, sseActive } = useUserActivityStream(
    apiBaseUrl ?? '',
    snapshot.eventId,
    targetId
  );

  const [fetchedTimeline, setFetchedTimeline] = useState<TimelineEntry[]>([]);

  useEffect(() => {
    if (targetId && snapshot.eventId) {
      fetchParticipantTimeline(apiBaseUrl ?? '', snapshot.eventId, targetId)
        .then(setFetchedTimeline)
        .catch(console.error);
    } else {
      setFetchedTimeline([]);
    }
  }, [targetId, snapshot.eventId, apiBaseUrl]);

  // If we have live activities from SSE, use them; otherwise fallback to the fetched timeline
  const activitiesToRender = useMemo(() => {
    if (!targetParticipant) return [];
    if (liveActivities.length > 0) {
      return liveActivities.map((act, index) => ({
        id: `live-${targetId}-${index}`,
        label: act.label,
        message: act.message,
      }));
    }
    return fetchedTimeline.map((entry, index) => ({
      id: `fetched-${targetId}-${index}`,
      label: entry.label,
      message: entry.message,
    })).reverse();
  }, [targetParticipant, liveActivities, fetchedTimeline, targetId]);

  const getLabelClass = (label: string) => {
    const l = label.toUpperCase();
    if (l.includes('INTENT') || l.includes('의도')) return 'activity-intent';
    if (l.includes('THINKING') || l.includes('탐색') || l.includes('판단') || l.includes('생각')) return 'activity-thinking';
    if (l.includes('ACTION') || l.includes('시도') || l.includes('행동')) return 'activity-action';
    if (l.includes('RETRY') || l.includes('다시') || l.includes('재시도')) return 'activity-retry';
    if (l.includes('SUCCESS') || l.includes('성공') || l.includes('완료')) return 'activity-success';
    if (l.includes('WAITING') || l.includes('대기')) return 'activity-waiting';
    if (l.includes('FAILED') || l.includes('실패') || l.includes('오류')) return 'activity-retry';
    return '';
  };

  return (
    <section className="panel activity-panel-wide">
      <div className="panel-title-row">
        <span className="eyebrow">LIVE MONITORING</span>
        <h2>활동 모니터링</h2>
      </div>

      <div className="activity-horizontal-layout" style={{ gridTemplateColumns: '1fr 1fr' }}>
        {/* 왼쪽 컬럼: 내 진행 또는 선택된 참가자 진행 */}
        <div className="activity-column live-stream">
          <h3 style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {targetParticipant ? (isMe ? '내 진행' : `${targetParticipant.displayName}의 진행`) : '진행 정보'}
            {targetParticipant && sseActive && !isMe && (
              <span className="live-indicator-badge" style={{ fontSize: '10px', color: 'var(--mint)', fontWeight: 'bold' }}>
                <span className="pulsing-dot" style={{ display: 'inline-block', width: '6px', height: '6px', backgroundColor: 'var(--mint)', borderRadius: '50%', marginRight: '4px' }}></span>
                LIVE
              </span>
            )}
          </h3>
          <div className="log-scroll-container">
            {activitiesToRender.length === 0 ? (
              <p className="empty-log">기록이 없습니다.</p>
            ) : (
              <ol className="activity-list" style={{ paddingLeft: 0 }}>
                {activitiesToRender.map((entry) => (
                  <li key={entry.id} className={`activity-item ${getLabelClass(entry.label)}`}>
                    <span className="activity-label">{entry.label}</span>
                    <p>{entry.message}</p>
                  </li>
                ))}
              </ol>
            )}
          </div>
        </div>

        {/* 오른쪽 컬럼: 안내 메시지 또는 전체 로그 */}
        <div className="activity-column history-log">
          <h3>시스템 알림</h3>
          <div className="log-scroll-container" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px', textAlign: 'center' }}>
            <div style={{ color: 'var(--text-muted, #888)', fontSize: '14px', lineHeight: '1.6' }}>
              <div style={{ fontSize: '24px', marginBottom: '10px' }}>⚡</div>
              <strong style={{ color: 'var(--mint)', display: 'block', marginBottom: '8px' }}>네트워크 최적화 활성화됨</strong>
              실시간 대역폭 최적화를 위해 전체 참가자의 상세 로그는 메인 스트림에서 제외되었습니다. 
              좌측 <strong>참가자 대기열 목록</strong>에서 분석할 참가자를 선택하시면 
              상세 타임라인을 온디맨드로 조회하거나 실시간 스트리밍으로 모니터링할 수 있습니다.
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}


