// ═══════════════════════════════════════════════════════
//  KidWatch Parent Dashboard — dashboard.js
//  Replace firebaseConfig with your actual Firebase config
// ═══════════════════════════════════════════════════════

const firebaseConfig = {
    apiKey: "YOUR_API_KEY",
    authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
    projectId: "YOUR_PROJECT_ID",
    storageBucket: "YOUR_PROJECT_ID.appspot.com",
    messagingSenderId: "YOUR_SENDER_ID",
    appId: "YOUR_APP_ID"
};

firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const db = firebase.firestore();
const storage = firebase.storage();

// ─── App State ────────────────────────────────────────────────────────────────
let currentDeviceId = null;
let allSms = [];
let allCalls = [];
let allScreenshots = [];
let activeTab = 'overview';
let unsubscribers = [];

// ─── Auth State ───────────────────────────────────────────────────────────────
auth.onAuthStateChanged(user => {
    if (user) {
        showDashboard();
        const savedDevice = localStorage.getItem('kidwatch_device_id');
        if (savedDevice) {
            document.getElementById('device-id-input').value = savedDevice;
            linkDevice();
        }
    } else {
        showLogin();
    }
});

// ─── Login / Register ─────────────────────────────────────────────────────────
async function handleLogin() {
    const email = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-password').value;
    const btn = document.getElementById('login-btn');
    const errEl = document.getElementById('login-error');

    if (!email || !password) { showError('Please enter email and password.'); return; }

    btn.disabled = true;
    btn.textContent = 'Signing in...';
    errEl.style.display = 'none';

    try {
        await auth.signInWithEmailAndPassword(email, password);
    } catch (e) {
        showError(e.message);
        btn.disabled = false;
        btn.textContent = 'Sign In';
    }
}

async function handleRegister() {
    const email = prompt('Enter email for new account:');
    const password = prompt('Enter password (min 6 chars):');
    if (!email || !password) return;

    try {
        await auth.createUserWithEmailAndPassword(email, password);
        alert('Account created! You are now signed in.');
    } catch (e) {
        alert('Error: ' + e.message);
    }
}

function handleLogout() {
    unsubscribeAll();
    auth.signOut();
}

function showError(msg) {
    const el = document.getElementById('login-error');
    el.textContent = msg;
    el.style.display = 'block';
}

// ─── Screen Switching ─────────────────────────────────────────────────────────
function showLogin() {
    document.getElementById('login-screen').style.display = 'flex';
    document.getElementById('dashboard-screen').style.display = 'none';
}

function showDashboard() {
    document.getElementById('login-screen').style.display = 'none';
    document.getElementById('dashboard-screen').style.display = 'flex';
}

// ─── Device Linking ───────────────────────────────────────────────────────────
function linkDevice() {
    const id = document.getElementById('device-id-input').value.trim().toUpperCase();
    if (!id) return;

    unsubscribeAll();
    currentDeviceId = id;
    localStorage.setItem('kidwatch_device_id', id);

    // Listen to device heartbeat
    const unsub0 = db.collection('devices').doc(id).onSnapshot(snap => {
        if (snap.exists) {
            const data = snap.data();
            const lastSeen = data.lastSeen ? timeAgo(data.lastSeen) : 'Never';
            document.getElementById('stat-lastseen').textContent = lastSeen;

            const isRecent = data.lastSeen && (Date.now() - data.lastSeen < 5 * 60 * 1000);
            const badge = document.getElementById('device-status');
            badge.textContent = isRecent ? '● Online' : `Last seen ${lastSeen}`;
            badge.className = 'status-badge' + (isRecent ? ' online' : '');
        }
    });
    unsubscribers.push(unsub0);

    // Listen to screenshots
    const unsub1 = db.collection('devices').doc(id).collection('screenshots')
        .orderBy('capturedAt', 'desc').limit(50)
        .onSnapshot(snap => {
            allScreenshots = snap.docs.map(d => ({ id: d.id, ...d.data() }));
            document.getElementById('stat-screenshots').textContent = allScreenshots.length;
            renderScreenshots();
            renderOverviewScreenshots();
        });
    unsubscribers.push(unsub1);

    // Listen to SMS
    const unsub2 = db.collection('devices').doc(id).collection('sms')
        .orderBy('timestamp', 'desc').limit(200)
        .onSnapshot(snap => {
            allSms = snap.docs.map(d => ({ id: d.id, ...d.data() }));
            document.getElementById('stat-sms').textContent = allSms.length;
            renderSms(allSms);
            renderOverviewSms();
        });
    unsubscribers.push(unsub2);

    // Listen to calls
    const unsub3 = db.collection('devices').doc(id).collection('calls')
        .orderBy('timestamp', 'desc').limit(200)
        .onSnapshot(snap => {
            allCalls = snap.docs.map(d => ({ id: d.id, ...d.data() }));
            document.getElementById('stat-calls').textContent = allCalls.length;
            renderCalls(allCalls);
            renderOverviewCalls();
        });
    unsubscribers.push(unsub3);
}

function unsubscribeAll() {
    unsubscribers.forEach(fn => fn());
    unsubscribers = [];
}

// ─── Tab Switching ────────────────────────────────────────────────────────────
function switchTab(tab) {
    activeTab = tab;

    document.querySelectorAll('.tab-content').forEach(el => el.style.display = 'none');
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));

    document.getElementById('tab-' + tab).style.display = 'block';
    document.querySelector(`[data-tab="${tab}"]`).classList.add('active');

    const titles = { overview: 'Overview', screenshots: 'Screenshots', sms: 'SMS Logs', calls: 'Call Logs' };
    document.getElementById('tab-title').textContent = titles[tab];

    // Show/hide filter controls
    const filterEl = document.getElementById('filter-type');
    filterEl.style.display = (tab === 'sms' || tab === 'calls') ? 'block' : 'none';
}

// ─── Render Screenshots ───────────────────────────────────────────────────────
function renderScreenshots() {
    const container = document.getElementById('screenshots-grid');
    const empty = document.getElementById('screenshots-empty');

    if (allScreenshots.length === 0) {
        container.innerHTML = '';
        empty.style.display = 'block';
        return;
    }
    empty.style.display = 'none';
    container.innerHTML = allScreenshots.map(s => `
        <div class="screenshot-card" onclick="openLightbox('${s.storageUrl}', ${s.capturedAt})">
            <img src="${s.storageUrl}" alt="Screenshot" loading="lazy" onerror="this.src='data:image/svg+xml,<svg xmlns=\\'http://www.w3.org/2000/svg\\' width=\\'200\\' height=\\'140\\'><rect width=\\'200\\' height=\\'140\\' fill=\\'%23eee\\'/><text x=\\'50%\\' y=\\'50%\\' text-anchor=\\'middle\\' dy=\\'.3em\\' fill=\\'%23999\\'\\'>Loading...</text></svg>'">
            <div class="screenshot-time">${formatDate(s.capturedAt)}</div>
        </div>
    `).join('');
}

function renderOverviewScreenshots() {
    const container = document.getElementById('overview-screenshots');
    const recent = allScreenshots.slice(0, 6);
    container.innerHTML = recent.map(s => `
        <div class="screenshot-card" onclick="openLightbox('${s.storageUrl}', ${s.capturedAt})">
            <img src="${s.storageUrl}" alt="Screenshot" loading="lazy">
            <div class="screenshot-time">${formatDate(s.capturedAt)}</div>
        </div>
    `).join('');
}

function openLightbox(url, ts) {
    document.getElementById('lightbox-img').src = url;
    document.getElementById('lightbox-time').textContent = formatDate(ts);
    document.getElementById('lightbox').style.display = 'flex';
}

function closeLightbox() {
    document.getElementById('lightbox').style.display = 'none';
}

// ─── Render SMS ───────────────────────────────────────────────────────────────
function renderSms(items) {
    const container = document.getElementById('sms-list');
    const empty = document.getElementById('sms-empty');

    if (items.length === 0) {
        container.innerHTML = '';
        empty.style.display = 'block';
        return;
    }
    empty.style.display = 'none';
    container.innerHTML = items.map(s => smsItemHtml(s)).join('');
}

function renderOverviewSms() {
    document.getElementById('overview-sms').innerHTML =
        allSms.slice(0, 5).map(s => smsItemHtml(s)).join('');
}

function smsItemHtml(s) {
    const badgeClass = s.type === 'INCOMING' ? 'badge-incoming' : 'badge-outgoing';
    const name = s.contactName ? `<div class="log-name">${s.contactName}</div>` : '';
    return `
        <div class="log-item">
            <span class="log-type-badge ${badgeClass}">${s.type}</span>
            <div class="log-body">
                <div class="log-number">${s.address}</div>
                ${name}
                <div class="log-text">${escHtml(s.body)}</div>
                <div class="log-meta"><span>${formatDate(s.timestamp)}</span></div>
            </div>
        </div>
    `;
}

// ─── Render Calls ─────────────────────────────────────────────────────────────
function renderCalls(items) {
    const container = document.getElementById('calls-list');
    const empty = document.getElementById('calls-empty');

    if (items.length === 0) {
        container.innerHTML = '';
        empty.style.display = 'block';
        return;
    }
    empty.style.display = 'none';
    container.innerHTML = items.map(c => callItemHtml(c)).join('');
}

function renderOverviewCalls() {
    document.getElementById('overview-calls').innerHTML =
        allCalls.slice(0, 5).map(c => callItemHtml(c)).join('');
}

function callItemHtml(c) {
    const badgeMap = { INCOMING: 'badge-incoming', OUTGOING: 'badge-outgoing', MISSED: 'badge-missed' };
    const name = c.contactName ? `<div class="log-name">${c.contactName}</div>` : '';
    const dur = c.type !== 'MISSED' ? `<span>${formatDuration(c.duration)}</span>` : '';
    return `
        <div class="log-item">
            <span class="log-type-badge ${badgeMap[c.type] || 'badge-outgoing'}">${c.type}</span>
            <div class="log-body">
                <div class="log-number">${c.number}</div>
                ${name}
                <div class="log-meta"><span>${formatDate(c.timestamp)}</span>${dur}</div>
            </div>
        </div>
    `;
}

// ─── Search & Filter ──────────────────────────────────────────────────────────
function handleSearch(query) {
    const q = query.toLowerCase();
    const typeFilter = document.getElementById('filter-type').value;

    if (activeTab === 'sms') {
        const filtered = allSms.filter(s =>
            (s.address?.toLowerCase().includes(q) ||
             s.body?.toLowerCase().includes(q) ||
             s.contactName?.toLowerCase().includes(q)) &&
            (typeFilter === 'all' || s.type === typeFilter)
        );
        renderSms(filtered);
    }

    if (activeTab === 'calls') {
        const filtered = allCalls.filter(c =>
            (c.number?.toLowerCase().includes(q) ||
             c.contactName?.toLowerCase().includes(q)) &&
            (typeFilter === 'all' || c.type === typeFilter)
        );
        renderCalls(filtered);
    }
}

function handleFilter(value) {
    handleSearch(document.getElementById('search-input').value);
}

// ─── Utility Functions ────────────────────────────────────────────────────────
function formatDate(ts) {
    if (!ts) return '';
    const d = new Date(ts);
    return d.toLocaleString('en-IN', {
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
}

function timeAgo(ts) {
    const diff = Date.now() - ts;
    const m = Math.floor(diff / 60000);
    if (m < 1) return 'Just now';
    if (m < 60) return `${m}m ago`;
    const h = Math.floor(m / 60);
    if (h < 24) return `${h}h ago`;
    return `${Math.floor(h / 24)}d ago`;
}

function formatDuration(secs) {
    if (!secs) return '0s';
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return m > 0 ? `${m}m ${s}s` : `${s}s`;
}

function escHtml(str) {
    return String(str || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

// ─── Keyboard shortcuts ───────────────────────────────────────────────────────
document.addEventListener('keydown', e => {
    if (e.key === 'Escape') closeLightbox();
});
