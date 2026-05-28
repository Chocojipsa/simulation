interface ControlPanelProps {
  virtualUserCount: number;
  concurrency: number;
  running: boolean;
  onVirtualUserCountChange: (value: number) => void;
  onConcurrencyChange: (value: number) => void;
  onStart: () => void;
}

export function ControlPanel({
  virtualUserCount,
  concurrency,
  running,
  onVirtualUserCountChange,
  onConcurrencyChange,
  onStart,
}: ControlPanelProps) {
  return (
    <section className="panel control-panel">
      <h2>시뮬레이션 제어</h2>
      <div className="segmented">
        {[30, 150, 300].map((value) => (
          <button key={value} className={virtualUserCount === value ? 'active' : ''} onClick={() => onVirtualUserCountChange(value)}>
            {value}명
          </button>
        ))}
      </div>
      <div className="segmented">
        {[10, 50, 100].map((value) => (
          <button key={value} className={concurrency === value ? 'active' : ''} onClick={() => onConcurrencyChange(value)}>
            동시성 {value}
          </button>
        ))}
      </div>
      <button className="primary-action" disabled={running} onClick={onStart}>
        시뮬레이션 시작
      </button>
    </section>
  );
}
