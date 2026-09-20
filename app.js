// EventHub Frontend Application Logic
const API_BASE = '/api';
let currentUser = null;
let currentEventId = null;
let currentEventData = null;
let allSeats = [];
let selectedSeatIds = [];
let isSeatsLockedByMe = false;
let pollingInterval = null;
let lockCountdownTimer = null;
let pendingBooking = null;

// Initial Auth State
function initAuthUI() {
    const token = localStorage.getItem('eh_token');
    const userStr = localStorage.getItem('eh_user');

    if (token && userStr) {
        currentUser = JSON.parse(userStr);
        document.getElementById('authSection').style.display = 'none';
        const userSec = document.getElementById('userSection');
        if (userSec) userSec.style.display = 'flex';
        const nameDisp = document.getElementById('userNameDisplay');
        if (nameDisp) nameDisp.textContent = currentUser.name || currentUser.email;

        const myBookingsNav = document.getElementById('myBookingsNavBtn');
        if (myBookingsNav) myBookingsNav.style.display = 'inline-block';
    } else {
        currentUser = null;
        document.getElementById('authSection').style.display = 'flex';
        const userSec = document.getElementById('userSection');
        if (userSec) userSec.style.display = 'none';
        const myBookingsNav = document.getElementById('myBookingsNavBtn');
        if (myBookingsNav) myBookingsNav.style.display = 'none';
    }
}

function getAuthHeaders() {
    const token = localStorage.getItem('eh_token');
    return {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
    };
}

// Modal Helpers
function openModal(modalId) {
    document.getElementById(modalId).classList.add('open');
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('open');
}

// Authentication
async function handleLogin(e) {
    e.preventDefault();
    const email = document.getElementById('loginEmail').value;
    const password = document.getElementById('loginPassword').value;
    const errDiv = document.getElementById('loginError');

    try {
        const res = await fetch(`${API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });

        if (!res.ok) {
            const err = await res.text();
            throw new Error(err || 'Invalid credentials');
        }

        const data = await res.json();
        localStorage.setItem('eh_token', data.token);
        localStorage.setItem('eh_user', JSON.stringify({ email: data.email, name: data.name, role: data.role }));
        closeModal('loginModal');
        initAuthUI();
        if (window.location.pathname.includes('event-details.html')) {
            loadSeats();
        }
    } catch (err) {
        errDiv.textContent = 'Login failed. Check email and password.';
        errDiv.style.display = 'block';
    }
}

async function handleRegister(e) {
    e.preventDefault();
    const name = document.getElementById('regName').value;
    const email = document.getElementById('regEmail').value;
    const password = document.getElementById('regPassword').value;
    const errDiv = document.getElementById('regError');

    try {
        const res = await fetch(`${API_BASE}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, email, password })
        });

        if (!res.ok) {
            const err = await res.text();
            throw new Error(err || 'Registration failed');
        }

        const data = await res.json();
        localStorage.setItem('eh_token', data.token);
        localStorage.setItem('eh_user', JSON.stringify({ email: data.email, name: data.name, role: data.role }));
        closeModal('registerModal');
        initAuthUI();
    } catch (err) {
        errDiv.textContent = err.message || 'Registration failed.';
        errDiv.style.display = 'block';
    }
}

function logout() {
    localStorage.removeItem('eh_token');
    localStorage.removeItem('eh_user');
    initAuthUI();
    if (window.location.pathname.includes('event-details.html')) {
        window.location.reload();
    }
}

// Events Listing Page
let loadedEvents = [];

async function loadEvents() {
    const listEl = document.getElementById('eventsList');
    if (!listEl) return;

    try {
        const res = await fetch(`${API_BASE}/events`);
        loadedEvents = await res.json();
        renderEvents(loadedEvents);
    } catch (err) {
        listEl.innerHTML = `<p class="alert-error">Failed to load events. Make sure backend is running.</p>`;
    }
}

function renderEvents(events) {
    const listEl = document.getElementById('eventsList');
    if (!events || events.length === 0) {
        listEl.innerHTML = `<p>No events available right now.</p>`;
        return;
    }

    listEl.innerHTML = events.map(ev => `
        <div class="card event-card">
            <div>
                <span class="event-tag">${ev.category}</span>
                <h3 class="event-title">${ev.title}</h3>
                <div class="event-meta">
                    <span>📍 ${ev.venueName}, ${ev.venueCity}</span>
                    <span>📅 ${new Date(ev.eventDate).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute:'2-digit' })}</span>
                    <span>🪑 ${ev.availableSeats} of ${ev.totalSeats} seats available</span>
                </div>
                <p style="color:#94a3b8; font-size:0.9rem;">${ev.description}</p>
            </div>
            <div class="event-footer">
                <div class="event-price">₹${ev.pricePerSeat} <span style="font-size:0.75rem; color:#94a3b8;">/ seat</span></div>
                <button class="btn btn-primary btn-sm" onclick="navigateToEvent(${ev.id})">Book Tickets →</button>
            </div>
        </div>
    `).join('');
}

function filterEvents(category) {
    document.querySelectorAll('.filter-btn').forEach(btn => btn.classList.remove('active'));
    event.target.classList.add('active');

    if (category === 'all') {
        renderEvents(loadedEvents);
    } else {
        renderEvents(loadedEvents.filter(e => e.category.toLowerCase() === category.toLowerCase()));
    }
}

function navigateToEvent(eventId) {
    window.location.href = `event-details.html?id=${eventId}`;
}

// Event Details & Seating Selection
function initEventDetailsPage() {
    const params = new URLSearchParams(window.location.search);
    currentEventId = params.get('id');

    if (!currentEventId) {
        alert('No event ID provided.');
        window.location.href = 'index.html';
        return;
    }

    loadEventInfo();
    loadSeats();

    // Start HTTP Polling every 3 seconds for live seat status
    if (pollingInterval) clearInterval(pollingInterval);
    pollingInterval = setInterval(loadSeats, 3000);
}

async function loadEventInfo() {
    const headerCard = document.getElementById('eventHeaderCard');
    try {
        const res = await fetch(`${API_BASE}/events/${currentEventId}`);
        currentEventData = await res.json();

        headerCard.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:flex-start; flex-wrap:wrap; gap:1rem;">
                <div>
                    <span class="event-tag">${currentEventData.category}</span>
                    <h2 style="font-size:1.8rem; margin:0.4rem 0;">${currentEventData.title}</h2>
                    <p style="color:#94a3b8; max-width:650px;">${currentEventData.description}</p>
                </div>
                <div style="text-align:right;">
                    <div style="font-size:1.5rem; font-weight:700; color:#34d399;">₹${currentEventData.pricePerSeat}</div>
                    <div style="color:#94a3b8; font-size:0.85rem;">Venue: ${currentEventData.venueName} (${currentEventData.venueCity})</div>
                </div>
            </div>
        `;
    } catch (err) {
        headerCard.innerHTML = `<p class="alert-error">Failed to load event header.</p>`;
    }
}

async function loadSeats() {
    const gridEl = document.getElementById('seatingGrid');
    if (!gridEl) return;

    try {
        const res = await fetch(`${API_BASE}/events/${currentEventId}/seats`, {
            headers: getAuthHeaders()
        });
        if (!res.ok) return;

        allSeats = await res.json();
        renderSeats(allSeats);
    } catch (err) {
        console.error('Seat polling error:', err);
    }
}

function renderSeats(seats) {
    const gridEl = document.getElementById('seatingGrid');

    // Group seats by row
    const rows = {};
    seats.forEach(s => {
        if (!rows[s.rowLabel]) rows[s.rowLabel] = [];
        rows[s.rowLabel].push(s);
    });

    let html = '';
    Object.keys(rows).sort().forEach(rowLabel => {
        html += `<div class="seat-row">`;
        html += `<span class="row-label">${rowLabel}</span>`;
        rows[rowLabel].forEach(seat => {
            let statusClass = 'available';
            let isClickable = true;

            if (seat.status === 'BOOKED') {
                statusClass = 'booked';
                isClickable = false;
            } else if (seat.status === 'LOCKED') {
                if (seat.isLockedByMe) {
                    statusClass = 'selected';
                } else {
                    statusClass = 'locked';
                    isClickable = false;
                }
            } else if (selectedSeatIds.includes(seat.id)) {
                statusClass = 'selected';
            }

            const clickAttr = isClickable ? `onclick="toggleSeatSelection(${seat.id})"` : '';
            html += `<div class="seat ${statusClass}" title="Seat ${seat.seatNumber} - ₹${seat.price}" ${clickAttr}>${seat.seatNumber}</div>`;
        });
        html += `</div>`;
    });

    gridEl.innerHTML = html;
    updateSummary();
}

function toggleSeatSelection(seatId) {
    if (!currentUser) {
        alert('Please sign in first to select and reserve seats.');
        return;
    }

    if (isSeatsLockedByMe) {
        alert('You already have active reserved seats. Complete payment or wait for lock expiration.');
        return;
    }

    const idx = selectedSeatIds.indexOf(seatId);
    if (idx > -1) {
        selectedSeatIds.splice(idx, 1);
    } else {
        if (selectedSeatIds.length >= 6) {
            alert('You can select a maximum of 6 seats at a time.');
            return;
        }
        selectedSeatIds.push(seatId);
    }

    renderSeats(allSeats);
}

function updateSummary() {
    const countEl = document.getElementById('seatCountLabel');
    const seatsEl = document.getElementById('selectedSeatsLabel');
    const priceEl = document.getElementById('totalPriceLabel');
    const lockBtn = document.getElementById('lockSeatsBtn');

    if (!selectedSeatIds.length) {
        if (countEl) countEl.textContent = '0';
        if (seatsEl) seatsEl.textContent = 'None';
        if (priceEl) priceEl.textContent = '₹0.00';
        if (lockBtn && !isSeatsLockedByMe) lockBtn.disabled = true;
        return;
    }

    const selectedSeats = allSeats.filter(s => selectedSeatIds.includes(s.id));
    const totalPrice = selectedSeats.reduce((acc, s) => acc + Number(s.price), 0);

    if (countEl) countEl.textContent = selectedSeats.length;
    if (seatsEl) seatsEl.textContent = selectedSeats.map(s => s.seatNumber).join(', ');
    if (priceEl) priceEl.textContent = `₹${totalPrice.toFixed(2)}`;

    if (lockBtn && !isSeatsLockedByMe) {
        lockBtn.disabled = false;
    }
}

// Seat Locking via Redis
async function handleLockSeats() {
    if (!currentUser) {
        alert('Please sign in first.');
        return;
    }

    const lockBtn = document.getElementById('lockSeatsBtn');
    lockBtn.disabled = true;
    lockBtn.textContent = 'Acquiring Redis Lock...';

    try {
        const res = await fetch(`${API_BASE}/bookings/lock-seats`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                eventId: currentEventId,
                seatIds: selectedSeatIds
            })
        });

        const data = await res.json();
        if (!res.ok) {
            alert(data.message || 'Could not lock seats. Another user may have reserved them.');
            selectedSeatIds = [];
            loadSeats();
            return;
        }

        isSeatsLockedByMe = true;
        document.getElementById('lockStatusMsg').textContent = '✅ Seats temporarily locked in Redis.';
        lockBtn.style.display = 'none';
        document.getElementById('checkoutBtn').style.display = 'block';

        startLockCountdown(data.remainingSeconds || 600);
        loadSeats();
    } catch (err) {
        alert('Error communicating with seat lock service.');
    } finally {
        lockBtn.textContent = 'Reserve Seats (10m Hold)';
    }
}

function startLockCountdown(seconds) {
    const timerBox = document.getElementById('lockTimerBox');
    const countdownEl = document.getElementById('countdownTimer');
    timerBox.style.display = 'block';

    let rem = seconds;
    if (lockCountdownTimer) clearInterval(lockCountdownTimer);

    lockCountdownTimer = setInterval(() => {
        rem--;
        if (rem <= 0) {
            clearInterval(lockCountdownTimer);
            alert('Your 10-minute reservation has expired. The seats have been released back to the pool.');
            window.location.reload();
            return;
        }

        const mins = Math.floor(rem / 60);
        const secs = rem % 60;
        countdownEl.textContent = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }, 1000);
}

// Checkout & Payment Simulation
async function openCheckoutModal() {
    const selectedSeats = allSeats.filter(s => selectedSeatIds.includes(s.id));
    const totalPrice = selectedSeats.reduce((acc, s) => acc + Number(s.price), 0);

    document.getElementById('payEventTitle').textContent = currentEventData.title;
    document.getElementById('paySeats').textContent = selectedSeats.map(s => s.seatNumber).join(', ');
    document.getElementById('payTotalAmount').textContent = `₹${totalPrice.toFixed(2)}`;

    // Create pending booking with unique idempotency key
    const idempotencyKey = 'IDEM-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
    try {
        const res = await fetch(`${API_BASE}/bookings/checkout`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                eventId: currentEventId,
                seatIds: selectedSeatIds,
                idempotencyKey: idempotencyKey
            })
        });

        if (!res.ok) {
            const err = await res.json();
            alert(err.message || 'Checkout failed');
            return;
        }

        pendingBooking = await res.json();
        pendingBooking.idempotencyKey = idempotencyKey;
        openModal('checkoutModal');
    } catch (err) {
        alert('Error creating checkout order.');
    }
}

async function processPayment() {
    const simulateFailure = document.getElementById('simulateFailureCheckbox').checked;
    const payBtn = document.getElementById('payNowBtn');
    const msgDiv = document.getElementById('paymentResultMsg');

    payBtn.disabled = true;
    payBtn.textContent = 'Processing Payment & Kafka Event...';
    msgDiv.style.display = 'none';

    try {
        const res = await fetch(`${API_BASE}/payments/process`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                bookingId: pendingBooking.bookingId,
                idempotencyKey: pendingBooking.idempotencyKey,
                amount: pendingBooking.totalAmount,
                simulateFailure: simulateFailure
            })
        });

        const data = await res.json();
        if (data.success) {
            closeModal('checkoutModal');
            document.getElementById('confirmedRefCode').textContent = data.bookingReference;
            openModal('confirmationModal');
            if (lockCountdownTimer) clearInterval(lockCountdownTimer);
        } else {
            msgDiv.textContent = `❌ ${data.message}. Seats released.`;
            msgDiv.className = 'alert-error';
            msgDiv.style.display = 'block';
            setTimeout(() => {
                closeModal('checkoutModal');
                window.location.reload();
            }, 3000);
        }
    } catch (err) {
        msgDiv.textContent = 'Error processing payment transaction.';
        msgDiv.className = 'alert-error';
        msgDiv.style.display = 'block';
    } finally {
        payBtn.disabled = false;
        payBtn.textContent = 'Pay Now';
    }
}

// User Bookings View
async function showMyBookings() {
    document.getElementById('eventsView').style.display = 'none';
    const bView = document.getElementById('bookingsView');
    bView.style.display = 'block';

    const listEl = document.getElementById('bookingsList');
    listEl.innerHTML = `<div class="loading-spinner">Loading your confirmed tickets...</div>`;

    try {
        const res = await fetch(`${API_BASE}/bookings/my-bookings`, {
            headers: getAuthHeaders()
        });
        const bookings = await res.json();

        if (!bookings.length) {
            listEl.innerHTML = `<p style="color:#94a3b8;">No bookings found for your account.</p>`;
            return;
        }

        listEl.innerHTML = bookings.map(b => `
            <div class="card mt-2" style="border-left: 4px solid #34d399;">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <div>
                        <h4 style="font-size:1.1rem; color:#f1f5f9;">${b.eventTitle}</h4>
                        <p style="color:#94a3b8; font-size:0.85rem;">Venue: ${b.venueName} • Booked: ${new Date(b.createdAt).toLocaleDateString()}</p>
                        <p style="color:#38bdf8; font-size:0.9rem; margin-top:0.25rem;">Seats: <strong>${b.seatNumbers.join(', ')}</strong></p>
                    </div>
                    <div style="text-align:right;">
                        <span class="event-tag" style="background:#065f46; color:#34d399;">${b.status}</span>
                        <div style="font-size:1.1rem; font-weight:700; margin-top:0.25rem;">₹${b.totalAmount}</div>
                        <div style="font-family:monospace; font-size:0.75rem; color:#64748b;">${b.bookingReference}</div>
                    </div>
                </div>
            </div>
        `).join('');
    } catch (err) {
        listEl.innerHTML = `<p class="alert-error">Failed to fetch bookings.</p>`;
    }
}

function showEventsView() {
    document.getElementById('bookingsView').style.display = 'none';
    document.getElementById('eventsView').style.display = 'block';
}
