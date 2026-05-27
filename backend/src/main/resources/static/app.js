const seatStatusLabels = {
  AVAILABLE: "선택 가능",
  HELD: "임시 선점",
  PAYMENT_IN_PROGRESS: "결제 진행 중",
  RESERVED: "예약 완료",
};

const userStatusLabels = {
  CREATED: "생성됨",
  QUEUED: "대기 중",
  ADMITTED: "입장 완료",
  SELECTING_SEAT: "좌석 선택 중",
  SEAT_HELD: "좌석 선점",
  PAYMENT_IN_PROGRESS: "결제 진행 중",
  RESERVED: "예약 성공",
  FAILED: "실패",
  EXPIRED: "만료",
};

let currentSimulationId = null;
let selectedUserId = null;
let eventSource = null;

const elements = {
  form: document.querySelector("#startForm"),
  startButton: document.querySelector("#startButton"),
  userCount: document.querySelector("#userCount"),
  simulationStatus: document.querySelector("#simulationStatus"),
  seatMap: document.querySelector("#seatMap"),
  waitingQueue: document.querySelector("#waitingQueue"),
  waitingQueueCount: document.querySelector("#waitingQueueCount"),
  userList: document.querySelector("#userList"),
  userCountLabel: document.querySelector("#userCountLabel"),
  selectedUserLabel: document.querySelector("#selectedUserLabel"),
  timeline: document.querySelector("#timeline"),
  queueSize: document.querySelector("#queueSize"),
  admittedCount: document.querySelector("#admittedCount"),
  heldCount: document.querySelector("#heldCount"),
  paymentCount: document.querySelector("#paymentCount"),
  reservedCount: document.querySelector("#reservedCount"),
  failedCount: document.querySelector("#failedCount"),
};

elements.form.addEventListener("submit", async (event) => {
  event.preventDefault();
  await startSimulation(Number(elements.userCount.value));
});

renderEmptySeats();

async function startSimulation(virtualUserCount) {
  closeEventSource();
  selectedUserId = null;
  elements.startButton.disabled = true;
  elements.simulationStatus.textContent = "시뮬레이션 시작 중";

  const response = await fetch("/simulations", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ virtualUserCount }),
  });

  if (!response.ok) {
    elements.startButton.disabled = false;
    elements.simulationStatus.textContent = "시뮬레이션 시작 실패";
    return;
  }

  const result = await response.json();
  currentSimulationId = result.simulationId;
  elements.simulationStatus.textContent = result.message;

  const snapshot = await fetchSnapshot(currentSimulationId);
  renderSnapshot(snapshot);
  subscribe(currentSimulationId);
}

async function fetchSnapshot(simulationId) {
  const response = await fetch(`/simulations/${simulationId}`);
  return response.json();
}

function subscribe(simulationId) {
  eventSource = new EventSource(`/simulations/${simulationId}/events`);
  eventSource.addEventListener("snapshot", (event) => {
    renderSnapshot(JSON.parse(event.data));
  });
  eventSource.onerror = () => {
    elements.simulationStatus.textContent = "실시간 연결 재시도 중";
  };
}

function closeEventSource() {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
}

function renderSnapshot(snapshot) {
  elements.startButton.disabled = snapshot.running;
  elements.simulationStatus.textContent = snapshot.running ? "시뮬레이션 진행 중" : "시뮬레이션 완료";

  renderMetrics(snapshot.metrics);
  renderSeats(snapshot.seats);
  renderWaitingQueue(snapshot.users);
  renderUsers(snapshot.users);
  renderTimeline(snapshot.users);
}

function renderMetrics(metrics) {
  elements.queueSize.textContent = metrics.queueSize;
  elements.admittedCount.textContent = metrics.admittedCount;
  elements.heldCount.textContent = metrics.heldCount;
  elements.paymentCount.textContent = metrics.paymentInProgressCount;
  elements.reservedCount.textContent = metrics.reservedCount;
  elements.failedCount.textContent = metrics.failedCount;
}

function renderSeats(seats) {
  elements.seatMap.replaceChildren(...seats.map((seat) => {
    const button = document.createElement("button");
    button.className = `seat ${seat.status.toLowerCase()}`;
    button.type = "button";
    button.title = `${seat.label} ${seatStatusLabels[seat.status]}`;
    button.ariaLabel = `${seat.label} ${seatStatusLabels[seat.status]}`;
    button.textContent = seat.label;
    return button;
  }));
}

function renderUsers(users) {
  elements.userCountLabel.textContent = `${users.length}명`;
  if (!selectedUserId && users.length > 0) {
    selectedUserId = defaultSelectedUser(users).id;
  }

  elements.userList.replaceChildren(...users.map((user) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = user.id === selectedUserId ? "user-row selected" : "user-row";
    button.dataset.userId = user.id;
    button.innerHTML = `
      <span>${user.displayName}</span>
      <strong>${userStatusLabels[user.status]}</strong>
    `;
    button.addEventListener("click", () => {
      selectedUserId = user.id;
      renderUsers(users);
      renderTimeline(users);
    });
    return button;
  }));
}

function defaultSelectedUser(users) {
  return users.find((user) =>
    user.timeline.some((entry) => entry.message.includes("이미 선택된 좌석입니다.")),
  ) ?? users[0];
}

function renderWaitingQueue(users) {
  const queuedUsers = users.filter((user) => user.status === "QUEUED");
  elements.waitingQueueCount.textContent = `${queuedUsers.length}명`;

  if (queuedUsers.length === 0) {
    const empty = document.createElement("p");
    empty.className = "empty-message";
    empty.textContent = "대기 중인 사용자가 없습니다.";
    elements.waitingQueue.replaceChildren(empty);
    return;
  }

  const visibleUsers = queuedUsers.slice(0, 12);
  const rows = visibleUsers.map((user, index) => {
    const row = document.createElement("button");
    row.type = "button";
    row.className = "queue-row";
    row.innerHTML = `
      <span>${index + 1}</span>
      <strong>${user.displayName}</strong>
    `;
    row.addEventListener("click", () => {
      selectedUserId = user.id;
      renderUsers(users);
      renderTimeline(users);
    });
    return row;
  });

  if (queuedUsers.length > visibleUsers.length) {
    const more = document.createElement("p");
    more.className = "empty-message";
    more.textContent = `외 ${queuedUsers.length - visibleUsers.length}명 대기 중`;
    rows.push(more);
  }

  elements.waitingQueue.replaceChildren(...rows);
}

function renderTimeline(users) {
  const selectedUser = users.find((user) => user.id === selectedUserId);
  if (!selectedUser) {
    elements.selectedUserLabel.textContent = "선택 없음";
    elements.timeline.replaceChildren();
    return;
  }

  elements.selectedUserLabel.textContent = selectedUser.displayName;
  elements.timeline.replaceChildren(...selectedUser.timeline.map((entry) => {
    const item = document.createElement("li");
    item.innerHTML = `<span>${entry.label}</span><p>${entry.message}</p>`;
    return item;
  }));
}

function renderEmptySeats() {
  const seats = Array.from({ length: 120 }, (_, index) => {
    const row = String.fromCharCode(65 + Math.floor(index / 12));
    return {
      id: index + 1,
      label: `${row}-${(index % 12) + 1}`,
      status: "AVAILABLE",
    };
  });
  renderSeats(seats);
}
