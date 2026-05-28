import type { VirtualUserView } from '../api/simulationApi';
import { getUserStatusLabel } from '../domain/simulationSelectors';

interface UserPanelProps {
  users: VirtualUserView[];
  selectedUserId: string | null;
  onSelectUser: (userId: string) => void;
}

export function UserPanel({ users, selectedUserId, onSelectUser }: UserPanelProps) {
  const selectedUser = users.find((user) => user.id === selectedUserId) ?? users[0] ?? null;

  return (
    <section className="panel user-panel">
      <div className="panel-heading">
        <h2>가상 사용자</h2>
        <span>{users.length}명</span>
      </div>
      <div className="user-layout">
        <div className="user-list">
          {users.map((user) => (
            <button key={user.id} className={user.id === selectedUser?.id ? 'user-row active' : 'user-row'} onClick={() => onSelectUser(user.id)}>
              <span>{user.displayName}</span>
              <strong>{getUserStatusLabel(user.status)}</strong>
              <small>시도 {user.seatAttemptCount} · 충돌 {user.conflictCount}</small>
            </button>
          ))}
        </div>
        <div className="timeline">
          <h3>{selectedUser?.displayName ?? '선택된 사용자 없음'}</h3>
          {selectedUser?.timeline.map((entry, index) => (
            <div className="timeline-entry" key={`${entry.label}-${index}`}>
              <strong>{entry.label}</strong>
              <p>{entry.message}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
