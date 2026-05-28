import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders the Korean operations dashboard', () => {
    render(<App />);

    expect(screen.getByText('분산 좌석 예매 시뮬레이터')).toBeInTheDocument();
    expect(screen.getByText('시뮬레이션 시작')).toBeInTheDocument();
    expect(screen.getByText('실시간 좌석표')).toBeInTheDocument();
    expect(screen.getByText('서버 분산')).toBeInTheDocument();
    expect(screen.getByText('Redis 대기열')).toBeInTheDocument();
    expect(screen.getByText('Kafka 결제')).toBeInTheDocument();
    expect(screen.getByText('가상 사용자')).toBeInTheDocument();
  });
});
