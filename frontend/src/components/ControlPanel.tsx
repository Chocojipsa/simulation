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
  const scenarios = [
    { count: 30, label: '가벼운 테스트' },
    { count: 150, label: '충돌 확인' },
    { count: 300, label: '고부하 데모' },
  ];

  return (
    <section className="panel control-panel">
      <h2>시뮬레이션 제어</h2>
      <div className="segmented">
        {scenarios.map((scenario) => (
          <button
            key={scenario.count}
            className={virtualUserCount === scenario.count ? 'active' : ''}
            onClick={() => onVirtualUserCountChange(scenario.count)}
          >
            <strong>{scenario.count}명</strong>
            <span>{scenario.label}</span>
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
