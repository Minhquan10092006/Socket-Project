/**
 * SecureChat Web Client — WebSocket Chat Application
 *
 * Connects to the chat server via WebSocket, handles authentication,
 * sends/receives JSON messages, and renders them in the UI.
 *
 * Architecture:
 *   - WebSocket connection to ws://localhost:5001
 *   - JSON-based message protocol
 *   - Client-side message rendering (no page reloads)
 *
 * @author Socket-Project Team
 */

// ═══════════════════════════════════════════════════════════════════
//  Global State
// ═══════════════════════════════════════════════════════════════════

let ws = null;
let currentUser = null;
let authMode = 'login';
const WS_PORT = 5001; // Server TCP port + 1

// Color palette for user avatars
const AVATAR_COLORS = [
    '#6366f1', '#8b5cf6', '#ec4899', '#f43f5e', '#ef4444',
    '#f97316', '#eab308', '#22c55e', '#14b8a6', '#06b6d4',
    '#3b82f6', '#a855f7', '#d946ef', '#e11d48', '#0ea5e9'
];

// ═══════════════════════════════════════════════════════════════════
//  WebSocket Connection
// ═══════════════════════════════════════════════════════════════════

function connectWebSocket() {
    const wsUrl = `ws://${window.location.hostname || 'localhost'}:${WS_PORT}`;

    try {
        ws = new WebSocket(wsUrl);
    } catch (e) {
        showAuthError('Failed to connect to server. Make sure the server is running.');
        return;
    }

    ws.onopen = () => {
        console.log('[WS] Connected to server');
        updateConnectionStatus(true);
    };

    ws.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            handleServerMessage(data);
        } catch (e) {
            console.error('[WS] Invalid message:', event.data);
        }
    };

    ws.onclose = () => {
        console.log('[WS] Disconnected');
        updateConnectionStatus(false);

        // Auto-reconnect after 3 seconds if we were authenticated
        if (currentUser) {
            setTimeout(() => {
                showToast('Reconnecting...');
                connectWebSocket();
            }, 3000);
        }
    };

    ws.onerror = (error) => {
        console.error('[WS] Error:', error);
        showAuthError('Connection error. Is the server running?');
    };
}

// ═══════════════════════════════════════════════════════════════════
//  Message Handler
// ═══════════════════════════════════════════════════════════════════

function handleServerMessage(data) {
    switch (data.type) {
        case 'auth':
            handleAuthResponse(data);
            break;
        case 'chat':
            addChatMessage(data.from, data.content, data.from === currentUser ? 'own' : '');
            break;
        case 'pm':
            addChatMessage(data.from, data.content, 'pm');
            break;
        case 'system':
            handleSystemMessage(data);
            break;
        case 'history':
            addChatMessage('server', data.content, 'history');
            break;
        default:
            console.log('[WS] Unknown message type:', data.type);
    }
}

function handleAuthResponse(data) {
    const spinner = document.getElementById('auth-spinner');
    const btnText = document.getElementById('auth-btn-text');
    spinner.classList.add('hidden');
    btnText.textContent = authMode === 'login' ? 'Login' : 'Register';

    if (data.from === 'success') {
        currentUser = document.getElementById('auth-username').value.trim();
        showChatScreen();
    } else if (data.from === 'error') {
        showAuthError(data.content);
    }
}

function handleSystemMessage(data) {
    switch (data.from) {
        case 'users':
            updateUserList(data.content);
            break;
        case 'stats':
            addChatMessage('📊', data.content, 'system');
            break;
        case 'info':
            addChatMessage('ℹ️', data.content, 'system');
            break;
        case 'error':
            addChatMessage('⚠️', data.content, 'error');
            break;
        default:
            addChatMessage('server', data.content, 'system');
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Authentication
// ═══════════════════════════════════════════════════════════════════

function switchTab(mode) {
    authMode = mode;
    document.getElementById('tab-login').classList.toggle('active', mode === 'login');
    document.getElementById('tab-register').classList.toggle('active', mode === 'register');
    document.getElementById('auth-btn-text').textContent = mode === 'login' ? 'Login' : 'Register';
    document.getElementById('auth-error').classList.add('hidden');
    document.getElementById('auth-password').setAttribute('autocomplete',
        mode === 'login' ? 'current-password' : 'new-password');
}

function handleAuth(event) {
    event.preventDefault();

    const username = document.getElementById('auth-username').value.trim();
    const password = document.getElementById('auth-password').value;

    if (!username || !password) {
        showAuthError('Please fill in all fields.');
        return;
    }

    if (username.length < 3 || username.length > 20) {
        showAuthError('Username must be 3-20 characters.');
        return;
    }

    if (password.length < 4) {
        showAuthError('Password must be at least 4 characters.');
        return;
    }

    // Connect if not already connected
    if (!ws || ws.readyState !== WebSocket.OPEN) {
        connectWebSocket();
        // Wait for connection then send auth
        const checkConn = setInterval(() => {
            if (ws && ws.readyState === WebSocket.OPEN) {
                clearInterval(checkConn);
                sendAuthMessage(username, password);
            }
        }, 100);

        // Timeout after 5 seconds
        setTimeout(() => {
            clearInterval(checkConn);
            if (!ws || ws.readyState !== WebSocket.OPEN) {
                showAuthError('Connection timeout. Make sure the server is running.');
                const spinner = document.getElementById('auth-spinner');
                const btnText = document.getElementById('auth-btn-text');
                spinner.classList.add('hidden');
                btnText.textContent = authMode === 'login' ? 'Login' : 'Register';
            }
        }, 5000);
    } else {
        sendAuthMessage(username, password);
    }

    // Show loading
    const spinner = document.getElementById('auth-spinner');
    const btnText = document.getElementById('auth-btn-text');
    spinner.classList.remove('hidden');
    btnText.textContent = 'Connecting...';
    document.getElementById('auth-error').classList.add('hidden');
}

function sendAuthMessage(username, password) {
    ws.send(JSON.stringify({
        type: 'auth',
        action: authMode,
        username: username,
        password: password
    }));
}

function showAuthError(message) {
    const errorEl = document.getElementById('auth-error');
    errorEl.textContent = message;
    errorEl.classList.remove('hidden');
}

// ═══════════════════════════════════════════════════════════════════
//  Chat UI
// ═══════════════════════════════════════════════════════════════════

function showChatScreen() {
    document.getElementById('auth-screen').classList.remove('active');
    document.getElementById('chat-screen').classList.add('active');
    document.getElementById('display-username').textContent = currentUser;
    document.getElementById('user-avatar').textContent = currentUser.charAt(0).toUpperCase();
    document.getElementById('message-input').focus();

    // Request user list
    setTimeout(() => sendCommand('/list'), 500);
}

function sendMessage(event) {
    event.preventDefault();

    const input = document.getElementById('message-input');
    const message = input.value.trim();

    if (!message || !ws || ws.readyState !== WebSocket.OPEN) return;

    if (message.startsWith('/')) {
        // Handle as command
        ws.send(JSON.stringify({
            type: 'command',
            command: message
        }));
    } else {
        ws.send(JSON.stringify({
            type: 'chat',
            message: message
        }));
    }

    input.value = '';
    updateCharCount();
}

function sendCommand(cmd) {
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    ws.send(JSON.stringify({
        type: 'command',
        command: cmd
    }));
}

function addChatMessage(from, content, type = '') {
    const container = document.getElementById('messages');
    const messagesContainer = document.getElementById('messages-container');

    // Remove welcome message if it exists
    const welcome = container.querySelector('.welcome-message');
    if (welcome) welcome.remove();

    const msg = document.createElement('div');
    msg.className = `msg ${type}`;

    const avatarColor = getAvatarColor(from);
    const avatarChar = getAvatarChar(from);
    const timeStr = new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });

    // Handle multiline content
    const formattedContent = escapeHtml(content).replace(/\\n/g, '<br>');

    msg.innerHTML = `
        <div class="msg-avatar" style="background: ${avatarColor}">${avatarChar}</div>
        <div class="msg-body">
            <div class="msg-header">
                <span class="msg-sender">${escapeHtml(from)}</span>
                <span class="msg-time">${timeStr}</span>
            </div>
            <div class="msg-content">${formattedContent}</div>
        </div>
    `;

    container.appendChild(msg);

    // Auto-scroll to bottom
    messagesContainer.scrollTop = messagesContainer.scrollHeight;

    // Play notification sound for messages from others
    if (from !== currentUser && type !== 'history' && type !== 'system') {
        playNotificationSound();
    }
}

function updateUserList(usersStr) {
    const userList = document.getElementById('user-list');
    const users = usersStr.split('\n').filter(u => u.trim());

    if (users.length === 0) {
        userList.innerHTML = '<div class="user-item"><span style="color: var(--text-muted)">No users online</span></div>';
        return;
    }

    userList.innerHTML = users.map(user => {
        const cleanName = user.replace(' (you)', '').trim();
        const isYou = user.includes('(you)');
        return `
            <div class="user-item">
                <div class="user-dot"></div>
                <span class="user-name ${isYou ? 'is-you' : ''}">${escapeHtml(cleanName)}${isYou ? ' (you)' : ''}</span>
            </div>
        `;
    }).join('');

    document.getElementById('online-count').textContent = `${users.length} user${users.length !== 1 ? 's' : ''} online`;
}

function updateConnectionStatus(connected) {
    const badge = document.getElementById('connection-badge');
    if (connected) {
        badge.classList.remove('disconnected');
        badge.innerHTML = '<div class="status-dot"></div><span>Connected</span>';
    } else {
        badge.classList.add('disconnected');
        badge.innerHTML = '<div class="status-dot"></div><span>Disconnected</span>';
    }
}

function disconnect() {
    if (ws) {
        ws.close();
        ws = null;
    }
    currentUser = null;
    document.getElementById('chat-screen').classList.remove('active');
    document.getElementById('auth-screen').classList.add('active');
    document.getElementById('auth-username').value = '';
    document.getElementById('auth-password').value = '';
    document.getElementById('messages').innerHTML = `
        <div class="welcome-message">
            <div class="welcome-icon">💬</div>
            <h3>Welcome to SecureChat</h3>
            <p>All messages are end-to-end encrypted with AES-256-GCM</p>
        </div>
    `;
}

function toggleSidebar() {
    document.getElementById('sidebar').classList.toggle('collapsed');
}

// ═══════════════════════════════════════════════════════════════════
//  Utility Functions
// ═══════════════════════════════════════════════════════════════════

function getAvatarColor(name) {
    if (['server', 'SERVER', '📊', 'ℹ️', '⚠️'].includes(name)) {
        return 'var(--bg-elevated)';
    }
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
        hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

function getAvatarChar(name) {
    if (['📊', 'ℹ️', '⚠️'].includes(name)) return name;
    if (['server', 'SERVER'].includes(name)) return '🖥';
    return name.charAt(0).toUpperCase();
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function updateCharCount() {
    const input = document.getElementById('message-input');
    const counter = document.getElementById('char-count');
    counter.textContent = `${input.value.length}/2000`;
}

function showToast(message) {
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3500);
}

function playNotificationSound() {
    // Create a subtle notification beep using Web Audio API
    try {
        const ctx = new (window.AudioContext || window.webkitAudioContext)();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.frequency.value = 800;
        osc.type = 'sine';
        gain.gain.value = 0.05;
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.15);
        osc.start(ctx.currentTime);
        osc.stop(ctx.currentTime + 0.15);
    } catch (e) {
        // Audio not supported — silently ignore
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Event Listeners
// ═══════════════════════════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', () => {
    // Character counter
    document.getElementById('message-input').addEventListener('input', updateCharCount);

    // Enter key handler for message input
    document.getElementById('message-input').addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage(e);
        }
    });

    // Focus username input on load
    document.getElementById('auth-username').focus();
});
