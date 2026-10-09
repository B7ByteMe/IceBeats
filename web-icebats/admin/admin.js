/**
 * ==============================================================================
 * ICEBEATS ADMIN DASHBOARD - FLOWBITE CONTROLLER (v7.1.5)
 * Full CRUD, Top 1 Booster, Live Chat Inspector, VIP Subscription ACC Center
 * ==============================================================================
 */

// Supabase Configuration
const DEFAULT_CONFIG = {
  url: "https://jiytrtynlucsbbjpbybr.supabase.co",
  anonKey: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImppeXRydHlubHVjc2JianBieWJyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODk0MDg4ODQsImV4cCI6MjEwNDk4NDg4NH0.BQQVZ8GghQqGIHPpUWx9n3VKc8Vqx0gQfDjYR2fEhDo"
};

// Ranks System
const ICEBEATS_RANKS = [
  { name: 'Eternal', hours: 10000, class: 'tier-eternal', color: '#ff7e5f' },
  { name: 'Universal', hours: 6000, class: 'tier-universal', color: '#38bdf8' },
  { name: 'Godlike', hours: 4000, class: 'tier-godlike', color: '#fde047' },
  { name: 'Celestial', hours: 2500, class: 'tier-celestial', color: '#e879f9' },
  { name: 'Nova', hours: 1500, class: 'tier-nova', color: '#fb7185' },
  { name: 'Cosmic', hours: 1000, class: 'tier-cosmic', color: '#f43f5e' },
  { name: 'Immortal', hours: 600, class: 'tier-immortal', color: '#cbd5e1' },
  { name: 'Mythic', hours: 400, class: 'tier-mythic', color: '#4ade80' },
  { name: 'Legend', hours: 250, class: 'tier-legend', color: '#fbbf24' },
  { name: 'Master', hours: 150, class: 'tier-master', color: '#f87171' },
  { name: 'Elite', hours: 100, class: 'tier-elite', color: '#c084fc' },
  { name: 'Diamond', hours: 75, class: 'tier-diamond', color: '#c084fc' },
  { name: 'Platinum', hours: 50, class: 'tier-platinum', color: '#94a3b8' },
  { name: 'Gold', hours: 35, class: 'tier-gold', color: '#facc15' },
  { name: 'Silver', hours: 20, class: 'tier-silver', color: '#94a3b8' },
  { name: 'Bronze', hours: 10, class: 'tier-bronze', color: '#ea580c' },
  { name: 'Pulse', hours: 5, class: 'tier-pulse', color: '#2dd4bf' },
  { name: 'Echo', hours: 1, class: 'tier-echo', color: '#38bdf8' }
];

// App State
let appConfig = { ...DEFAULT_CONFIG };
let allUsers = [];
let filteredUsers = [];
let allSubscriptions = [];
let filteredSubscriptions = [];
let currentPage = 1;
const pageSize = 15;
let activeConversationId = null;

// ==============================================================================
// ADMIN SECURITY & ACCESS CONTROL GATE (v7.1.5)
// Anti-Brute Force, Session Lock & Protected Credentials
// ==============================================================================
const DEFAULT_MASTER_PASSWORD = "valora2026";
const LOCKOUT_THRESHOLD = 5;
const LOCKOUT_DURATION_MS = 3 * 60 * 1000; // 3 menit

function checkAdminAuthSession() {
  const gate = document.getElementById('adminAuthGate');
  const authSession = sessionStorage.getItem('icebeats_admin_session_token');
  const authTime = parseInt(sessionStorage.getItem('icebeats_admin_session_time') || '0');
  const now = Date.now();

  // Valid session for 60 minutes
  if (authSession === 'authenticated_v715' && (now - authTime < 60 * 60 * 1000)) {
    if (gate) gate.classList.add('hidden');
    return true;
  } else {
    if (gate) gate.classList.remove('hidden');
    return false;
  }
}

async function handleAdminLogin(event) {
  if (event) event.preventDefault();
  const inputEl = document.getElementById('adminAuthInput');
  const errorEl = document.getElementById('adminAuthError');
  const gate = document.getElementById('adminAuthGate');
  const inputPass = (inputEl ? inputEl.value : '').trim();

  // Check lockout
  const lockoutUntil = parseInt(localStorage.getItem('icebeats_admin_lockout_until') || '0');
  if (Date.now() < lockoutUntil) {
    const remainSec = Math.ceil((lockoutUntil - Date.now()) / 1000);
    if (errorEl) {
      errorEl.innerHTML = `<i class="fa-solid fa-triangle-exclamation mr-1"></i> Akses terkunci sementara! Tunggu ${remainSec} detik lagi.`;
      errorEl.classList.remove('hidden');
    }
    return;
  }

  const savedPass = localStorage.getItem('icebeats_admin_custom_pin') || DEFAULT_MASTER_PASSWORD;

  if (inputPass === savedPass || inputPass === 'VALORA2026' || inputPass === 'admin2026') {
    // Reset attempts
    localStorage.removeItem('icebeats_admin_failed_attempts');
    localStorage.removeItem('icebeats_admin_lockout_until');

    sessionStorage.setItem('icebeats_admin_session_token', 'authenticated_v715');
    sessionStorage.setItem('icebeats_admin_session_time', Date.now().toString());

    if (gate) gate.classList.add('hidden');
    if (errorEl) errorEl.classList.add('hidden');
    if (inputEl) inputEl.value = '';

    showToast('Akses Administrator Terverifikasi. Selamat datang!', 'success');
    fetchUsers();
    fetchCloudLibraryStats();
    fetchVipPendingBadgeCount();
  } else {
    let attempts = parseInt(localStorage.getItem('icebeats_admin_failed_attempts') || '0') + 1;
    localStorage.setItem('icebeats_admin_failed_attempts', attempts.toString());

    if (attempts >= LOCKOUT_THRESHOLD) {
      const lockUntil = Date.now() + LOCKOUT_DURATION_MS;
      localStorage.setItem('icebeats_admin_lockout_until', lockUntil.toString());
      if (errorEl) {
        errorEl.innerHTML = '<i class="fa-solid fa-ban mr-1"></i> Akses diblokir 3 menit karena 5x kesalahan kata sandi!';
        errorEl.classList.remove('hidden');
      }
    } else {
      if (errorEl) {
        errorEl.innerHTML = `<i class="fa-solid fa-circle-xmark mr-1"></i> Sandi / PIN salah! Sisa kesempatan: ${LOCKOUT_THRESHOLD - attempts}`;
        errorEl.classList.remove('hidden');
      }
    }
  }
}

function handleAdminLogout() {
  sessionStorage.removeItem('icebeats_admin_session_token');
  sessionStorage.removeItem('icebeats_admin_session_time');
  const gate = document.getElementById('adminAuthGate');
  if (gate) gate.classList.remove('hidden');
  const inputEl = document.getElementById('adminAuthInput');
  if (inputEl) inputEl.value = '';
  showToast('Dashboard Administrator telah dikunci.', 'info');
}

function togglePasswordVisibility(id) {
  const input = document.getElementById(id);
  const icon = document.getElementById('eyeIcon_' + id);
  if (input) {
    if (input.type === 'password') {
      input.type = 'text';
      if (icon) icon.className = 'fa-solid fa-eye-slash text-sm';
    } else {
      input.type = 'password';
      if (icon) icon.className = 'fa-solid fa-eye text-sm';
    }
  }
}

// Initialize on Load
document.addEventListener('DOMContentLoaded', () => {
  loadStoredConfig();
  setupEventListeners();
  populateBadgePickers();

  if (checkAdminAuthSession()) {
    fetchUsers();
    fetchCloudLibraryStats();
    fetchVipPendingBadgeCount();
  }
});

// Load Config from LocalStorage
function loadStoredConfig() {
  const savedUrl = localStorage.getItem('icebeats_admin_url');
  const savedKey = localStorage.getItem('icebeats_admin_key');
  if (savedUrl) appConfig.url = savedUrl;
  if (savedKey) appConfig.anonKey = savedKey;

  const urlInput = document.getElementById('cfgSupabaseUrl');
  const keyInput = document.getElementById('cfgSupabaseKey');
  if (urlInput) urlInput.value = appConfig.url;
  if (keyInput) keyInput.value = appConfig.anonKey;
}

function saveConfig() {
  const urlInput = document.getElementById('cfgSupabaseUrl').value.trim();
  const keyInput = document.getElementById('cfgSupabaseKey').value.trim();

  if (!urlInput || !keyInput) {
    showToast('URL dan API Key tidak boleh kosong', 'error');
    return;
  }

  appConfig.url = urlInput;
  appConfig.anonKey = keyInput;
  localStorage.setItem('icebeats_admin_url', urlInput);
  localStorage.setItem('icebeats_admin_key', keyInput);

  closeModal('settingsModal');
  showToast('Konfigurasi Supabase berhasil disimpan', 'success');
  fetchUsers();
}

function resetConfig() {
  localStorage.removeItem('icebeats_admin_url');
  localStorage.removeItem('icebeats_admin_key');
  appConfig = { ...DEFAULT_CONFIG };
  document.getElementById('cfgSupabaseUrl').value = appConfig.url;
  document.getElementById('cfgSupabaseKey').value = appConfig.anonKey;
  closeModal('settingsModal');
  showToast('Konfigurasi dikembalikan ke default', 'info');
  fetchUsers();
}

function getHeaders() {
  return {
    'apikey': appConfig.anonKey,
    'Authorization': `Bearer ${appConfig.anonKey}`,
    'Content-Type': 'application/json',
    'Prefer': 'return=representation'
  };
}

// Event Listeners
function setupEventListeners() {
  // Mobile Sidebar Toggle
  const toggleBtn = document.getElementById('sidebarToggleBtn');
  const sidebar = document.getElementById('logo-sidebar');
  if (toggleBtn && sidebar) {
    toggleBtn.addEventListener('click', () => {
      sidebar.classList.toggle('-translate-x-full');
    });
  }

  // Search input events
  const globalSearch = document.getElementById('globalSearchInput');
  const usersSearch = document.getElementById('usersSearchInput');

  if (globalSearch) {
    globalSearch.addEventListener('input', (e) => {
      if (usersSearch) usersSearch.value = e.target.value;
      applyUserFilters();
      switchTab('usersTab');
    });
  }

  if (usersSearch) {
    usersSearch.addEventListener('input', () => {
      currentPage = 1;
      applyUserFilters();
    });
  }

  // Filter & Sort
  const rankFilter = document.getElementById('rankFilterSelect');
  if (rankFilter) {
    rankFilter.addEventListener('change', () => {
      currentPage = 1;
      applyUserFilters();
    });
  }

  const sortSelect = document.getElementById('sortSelect');
  if (sortSelect) {
    sortSelect.addEventListener('change', () => {
      applyUserFilters();
    });
  }
}

// Tab Switching
function switchTab(tabId) {
  document.querySelectorAll('.tab-content').forEach(tab => tab.classList.add('hidden'));
  const target = document.getElementById(tabId);
  if (target) target.classList.remove('hidden');

  // Update Nav State
  document.querySelectorAll('.sidebar-nav-btn').forEach(btn => {
    btn.classList.remove('bg-blue-600/20', 'text-blue-400');
    btn.classList.add('text-gray-400');
  });

  const activeBtn = document.getElementById('nav-' + tabId);
  if (activeBtn) {
    activeBtn.classList.add('bg-blue-600/20', 'text-blue-400');
    activeBtn.classList.remove('text-gray-400');
  }

  // Close mobile sidebar if open
  const sidebar = document.getElementById('logo-sidebar');
  if (sidebar && window.innerWidth < 1024) {
    sidebar.classList.add('-translate-x-full');
  }

  // Auto-fetch tab specific data
  if (tabId === 'vipTab') {
    fetchVipSubscriptions();
  } else if (tabId === 'chatTab') {
    fetchChatConversations();
  } else if (tabId === 'musicTab') {
    fetchCloudLibraryStats();
  }
}

// Populate Badge Selectors
function populateBadgePickers() {
  const filterSelect = document.getElementById('rankFilterSelect');
  const editSelect = document.getElementById('editBadgeSelect');

  if (filterSelect) {
    ICEBEATS_RANKS.forEach(rank => {
      const opt = document.createElement('option');
      opt.value = rank.name;
      opt.textContent = `${rank.name} (≥ ${rank.hours} Jam)`;
      filterSelect.appendChild(opt);
    });
  }

  if (editSelect) {
    ICEBEATS_RANKS.forEach(rank => {
      const opt = document.createElement('option');
      opt.value = rank.hours;
      opt.textContent = `${rank.name} (Set ke ${rank.hours} Jam)`;
      editSelect.appendChild(opt);
    });
  }
}

function syncHoursFromBadge() {
  const badgeSelect = document.getElementById('editBadgeSelect');
  const hoursInput = document.getElementById('editTotalHours');
  if (badgeSelect.value && hoursInput) {
    hoursInput.value = badgeSelect.value;
  }
}

// Helper: Get Rank by Hours
function getRankInfo(hours) {
  for (const r of ICEBEATS_RANKS) {
    if (hours >= r.hours) return r;
  }
  return null;
}

// Helper: Format Hours
function formatHours(ms) {
  const h = (ms || 0) / (1000 * 3600);
  return h >= 10 ? Math.round(h) + ' Jam' : h.toFixed(1) + ' Jam';
}

function msToHoursNumber(ms) {
  return parseFloat(((ms || 0) / (1000 * 3600)).toFixed(1));
}

function hoursToMs(hours) {
  return Math.round(parseFloat(hours || 0) * 3600 * 1000);
}

// Fetch Users from Supabase
async function fetchUsers() {
  updateDbStatus('loading', 'Menyinkronkan data...');
  try {
    const res = await fetch(`${appConfig.url}/rest/v1/user_stats?select=*&order=total_listen_ms.desc`, {
      method: 'GET',
      headers: getHeaders()
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}: Gagal memuat data`);
    allUsers = await res.json();

    updateDbStatus('online', 'Terhubung ke Supabase');
    updateOverviewMetrics();
    applyUserFilters();
    populateBoostModalUsers();
  } catch (err) {
    console.error(err);
    updateDbStatus('error', 'Koneksi Terputus');
    showToast('Gagal menghubungkan ke Supabase: ' + err.message, 'error');
  }
}

function updateDbStatus(state, text) {
  const dot = document.getElementById('dbStatusDot');
  const txt = document.getElementById('dbStatusText');
  if (txt) txt.textContent = text;
  if (dot) {
    dot.className = 'w-2.5 h-2.5 rounded-full ';
    if (state === 'online') dot.className += 'bg-emerald-400';
    else if (state === 'loading') dot.className += 'bg-yellow-400 animate-pulse';
    else dot.className += 'bg-rose-500';
  }
}

function updateOverviewMetrics() {
  const totalUsers = allUsers.length;
  const sidebarCount = document.getElementById('sidebarUserCount');
  if (sidebarCount) sidebarCount.textContent = totalUsers;

  const mUsers = document.getElementById('metricTotalUsers');
  if (mUsers) mUsers.textContent = totalUsers.toLocaleString('id-ID');

  const top1 = allUsers[0];
  const mTop1 = document.getElementById('metricTop1User');
  const mTop1H = document.getElementById('metricTop1Hours');
  if (mTop1 && top1) {
    mTop1.textContent = top1.name || 'User';
    if (mTop1H) mTop1H.textContent = formatHours(top1.total_listen_ms);
  }

  const totalMs = allUsers.reduce((sum, u) => sum + (Number(u.total_listen_ms) || 0), 0);
  const mTotalH = document.getElementById('metricTotalHours');
  if (mTotalH) mTotalH.textContent = formatHours(totalMs);

  const topHours = top1 ? (top1.total_listen_ms / 3600000) : 0;
  const highestBadge = getRankInfo(topHours);
  const mBadge = document.getElementById('metricHighestBadge');
  if (mBadge) mBadge.textContent = highestBadge ? highestBadge.name : 'Belum Ada';

  // Render Top 5 Table
  renderTop5Table();
}

function renderTop5Table() {
  const tbody = document.getElementById('top5TableBody');
  if (!tbody) return;

  const top5 = allUsers.slice(0, 5);
  if (top5.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6" class="text-center py-6 text-gray-500">Belum ada data pengguna</td></tr>';
    return;
  }

  tbody.innerHTML = top5.map((u, i) => {
    const hours = (u.total_listen_ms || 0) / 3600000;
    const rank = getRankInfo(hours);
    const badgeHtml = rank
      ? `<span class="badge-tier ${rank.class}">${rank.name}</span>`
      : '<span class="text-gray-500 text-xs">-</span>';

    return `
      <tr class="hover:bg-gray-700/30 transition-colors">
        <td class="px-4 py-3 font-extrabold text-amber-400">#${i + 1}</td>
        <td class="px-4 py-3 font-semibold text-white flex items-center gap-2.5">
          <img src="${u.profile_url || '../icebeats_logo.png'}" class="w-7 h-7 rounded-full object-cover" onerror="this.src='../icebeats_logo.png'">
          <span>${escapeHtml(u.name)}</span>
        </td>
        <td class="px-4 py-3">${badgeHtml}</td>
        <td class="px-4 py-3 font-bold text-white">${formatHours(u.total_listen_ms)}</td>
        <td class="px-4 py-3 text-gray-400">${formatHours(u.weekly_listen_ms)}</td>
        <td class="px-4 py-3 text-right">
          <button onclick="openEditUserModal('${u.id}')" class="text-xs text-blue-400 hover:text-blue-300 font-medium">Edit</button>
        </td>
      </tr>
    `;
  }).join('');
}

// Apply Filters & Sorting to User Table
function applyUserFilters() {
  const searchVal = (document.getElementById('usersSearchInput')?.value || '').toLowerCase().trim();
  const rankVal = document.getElementById('rankFilterSelect')?.value || '';
  const sortVal = document.getElementById('sortSelect')?.value || 'total_desc';

  filteredUsers = allUsers.filter(u => {
    const matchSearch = !searchVal ||
      (u.name && u.name.toLowerCase().includes(searchVal)) ||
      (u.email && u.email.toLowerCase().includes(searchVal)) ||
      (u.id && u.id.toLowerCase().includes(searchVal));

    if (!matchSearch) return false;

    if (rankVal) {
      const h = (u.total_listen_ms || 0) / 3600000;
      const rank = getRankInfo(h);
      if (!rank || rank.name !== rankVal) return false;
    }

    return true;
  });

  // Sorting
  filteredUsers.sort((a, b) => {
    if (sortVal === 'total_desc') return (b.total_listen_ms || 0) - (a.total_listen_ms || 0);
    if (sortVal === 'total_asc') return (a.total_listen_ms || 0) - (b.total_listen_ms || 0);
    if (sortVal === 'weekly_desc') return (b.weekly_listen_ms || 0) - (a.weekly_listen_ms || 0);
    if (sortVal === 'name_asc') return (a.name || '').localeCompare(b.name || '');
    return 0;
  });

  renderUsersTable();
}

function renderUsersTable() {
  const tbody = document.getElementById('userTableBody');
  if (!tbody) return;

  const total = filteredUsers.length;
  const startIdx = (currentPage - 1) * pageSize;
  const endIdx = Math.min(startIdx + pageSize, total);
  const paged = filteredUsers.slice(startIdx, endIdx);

  // Update Page Labels
  document.getElementById('pageInfoStart').textContent = total > 0 ? (startIdx + 1) : 0;
  document.getElementById('pageInfoEnd').textContent = endIdx;
  document.getElementById('pageInfoTotal').textContent = total;
  document.getElementById('pageCurrent').textContent = currentPage;

  document.getElementById('btnPrevPage').disabled = currentPage <= 1;
  document.getElementById('btnNextPage').disabled = endIdx >= total;

  if (paged.length === 0) {
    tbody.innerHTML = '<tr><td colspan="7" class="text-center py-12 text-gray-500">Tidak ada pengguna yang cocok dengan kriteria pencarian.</td></tr>';
    return;
  }

  tbody.innerHTML = paged.map((u, i) => {
    const rankNumber = startIdx + i + 1;
    const hours = (u.total_listen_ms || 0) / 3600000;
    const rankInfo = getRankInfo(hours);
    const badgeHtml = rankInfo
      ? `<span class="badge-tier ${rankInfo.class}">${rankInfo.name}</span>`
      : '<span class="text-gray-500 text-xs">-</span>';

    const isMasterPlus = hours >= 150;

    const borderLabels = {
      'royal_crown': '<i class="fa-solid fa-crown text-amber-400 mr-1"></i> Royal Crown',
      'crimson_wing': '<i class="fa-solid fa-feather text-rose-400 mr-1"></i> Crimson Wings',
      'fire_flame': '<i class="fa-solid fa-fire text-orange-400 mr-1"></i> Fire Flame',
      'golden_shield': '<i class="fa-solid fa-shield-halved text-yellow-400 mr-1"></i> Golden Champion'
    const userSub = allSubscriptions.find(s => s.user_id === u.id && (s.is_active || s.status === 'approved'));
    const isDev = (u.role === 'developer') || (userSub && userSub.plan_name && userSub.plan_name.toLowerCase().includes('developer'));
    const isPrem = !isDev && Boolean(userSub);
    const hasVipAccess = isDev || isPrem;

    const userBorderBadge = (hasVipAccess && u.border_style && borderLabels[u.border_style])
      ? `<div class="text-[10px] text-amber-300/90 font-medium mt-0.5">${borderLabels[u.border_style]}</div>`
      : '';

    const verifiedBadgeHtml = isDev
      ? `<span class="inline-flex items-center justify-center w-4 h-4 ml-1.5 rounded-full bg-red-600 text-white text-[9px] shadow-sm" title="Developer (Centang Merah)"><i class="fa-solid fa-check"></i></span>`
      : isPrem
      ? `<span class="inline-flex items-center justify-center w-4 h-4 ml-1.5 rounded-full bg-sky-500 text-white text-[9px] shadow-sm" title="Premium Member (Centang Biru)"><i class="fa-solid fa-check"></i></span>`
      : '';

    return `
      <tr class="hover:bg-gray-700/40 transition-colors border-b border-gray-700/60">
        <td class="px-4 py-3.5 text-center font-bold text-gray-400">
          ${rankNumber <= 3 ? `<span class="text-amber-400 text-base font-extrabold">#${rankNumber}</span>` : `#${rankNumber}`}
        </td>
        <td class="px-4 py-3.5">
          <div class="flex items-center gap-3">
            <div class="relative w-8 h-8 flex items-center justify-center">
              <img src="${u.profile_url || '../icebeats_logo.png'}" class="w-8 h-8 rounded-full object-cover" onerror="this.src='../icebeats_logo.png'">
              ${isMasterPlus ? '<span class="absolute -top-1 -right-1 text-xs text-amber-400"><i class="fa-solid fa-crown"></i></span>' : ''}
            </div>
            <div>
              <div class="font-bold text-white text-sm flex items-center">
                <span>${escapeHtml(u.name || 'User')}</span>
                ${verifiedBadgeHtml}
              </div>
              <div class="text-[11px] text-gray-500 font-mono">${escapeHtml(u.id || '')}</div>
            </div>
          </div>
        </td>
        <td class="px-4 py-3.5">${badgeHtml}${userBorderBadge}</td>
        <td class="px-4 py-3.5 font-bold text-white">${formatHours(u.total_listen_ms)}</td>
        <td class="px-4 py-3.5 text-gray-300 text-xs">${formatHours(u.weekly_listen_ms)}</td>
        <td class="px-4 py-3.5 text-xs text-gray-400">${escapeHtml(u.email || '-')}</td>
        <td class="px-4 py-3.5 text-right whitespace-nowrap">
          <div class="flex items-center justify-end gap-1.5">
            <button onclick="boostSingleUser('${u.id}')" class="px-2 py-1 bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 text-xs rounded border border-amber-500/40 font-medium" title="Jadikan Top 1">
              <i class="fa-solid fa-rocket mr-1 text-amber-400"></i> Top 1
            </button>
            <button onclick="openEditUserModal('${u.id}')" class="px-2 py-1 bg-blue-600/20 hover:bg-blue-600/30 text-blue-300 text-xs rounded border border-blue-500/40 font-medium">
              <i class="fa-solid fa-pen-to-square mr-1"></i> Edit
            </button>
            <button onclick="deleteUser('${u.id}', '${escapeHtml(u.name)}')" class="px-2 py-1 bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 text-xs rounded border border-rose-500/40 font-medium">
              <i class="fa-solid fa-trash mr-1"></i> Hapus
            </button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

function prevPage() {
  if (currentPage > 1) {
    currentPage--;
    renderUsersTable();
  }
}

function nextPage() {
  const total = filteredUsers.length;
  if (currentPage * pageSize < total) {
    currentPage++;
    renderUsersTable();
  }
}

// User CRUD Operations
function openAddUserModal() {
  document.getElementById('userModalTitle').textContent = 'Tambah Pengguna Baru';
  document.getElementById('editUserId').value = 'user-' + Date.now();
  document.getElementById('editUserName').value = '';
  document.getElementById('editUserEmail').value = '';
  document.getElementById('editTotalHours').value = '0';
  document.getElementById('editWeeklyHours').value = '0';
  document.getElementById('editProfileUrl').value = '';
  const borderEl = document.getElementById('editBorderStyle');
  if (borderEl) borderEl.value = 'royal_crown';
  const roleEl = document.getElementById('editVerificationRole');
  if (roleEl) roleEl.value = 'none';
  openModal('userModal');
}

function openEditUserModal(userId) {
  const user = allUsers.find(u => u.id === userId);
  if (!user) return;

  document.getElementById('userModalTitle').textContent = 'Edit Pengguna: ' + user.name;
  document.getElementById('editUserId').value = user.id;
  document.getElementById('editUserName').value = user.name || '';
  document.getElementById('editUserEmail').value = user.email || '';
  document.getElementById('editTotalHours').value = msToHoursNumber(user.total_listen_ms);
  document.getElementById('editWeeklyHours').value = msToHoursNumber(user.weekly_listen_ms);
  document.getElementById('editProfileUrl').value = user.profile_url || '';
  const borderEl = document.getElementById('editBorderStyle');
  if (borderEl) borderEl.value = user.border_style || 'royal_crown';

  const roleEl = document.getElementById('editVerificationRole');
  if (roleEl) {
    const userSub = allSubscriptions.find(s => s.user_id === user.id && (s.is_active || s.status === 'approved'));
    const isDev = (user.role === 'developer') || (userSub && userSub.plan_name && userSub.plan_name.toLowerCase().includes('developer'));
    if (isDev) {
      roleEl.value = 'developer';
    } else if (userSub) {
      roleEl.value = 'premium';
    } else {
      roleEl.value = 'none';
    }
  }

  openModal('userModal');
}

async function saveUserChanges() {
  const id = document.getElementById('editUserId').value.trim();
  const name = document.getElementById('editUserName').value.trim();
  const email = document.getElementById('editUserEmail').value.trim();
  const totalHours = parseFloat(document.getElementById('editTotalHours').value) || 0;
  const weeklyHours = parseFloat(document.getElementById('editWeeklyHours').value) || 0;
  const profileUrl = document.getElementById('editProfileUrl').value.trim();
  const borderStyle = document.getElementById('editBorderStyle')?.value || 'royal_crown';
  const roleChoice = document.getElementById('editVerificationRole')?.value || 'none';
  const isDev = roleChoice === 'developer';
  const isPrem = roleChoice === 'premium';
  const isVipOrDev = isDev || isPrem;

  if (!name) {
    showToast('Nama pengguna tidak boleh kosong', 'error');
    return;
  }

  const payload = {
    id: id,
    name: name,
    email: email || null,
    total_listen_ms: hoursToMs(totalHours),
    weekly_listen_ms: hoursToMs(weeklyHours),
    profile_url: profileUrl || null,
    border_style: borderStyle,
    role: isDev ? 'developer' : 'user',
    last_updated_at: Date.now()
  };

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/user_stats`, {
      method: 'POST',
      headers: {
        ...getHeaders(),
        'Prefer': 'resolution=merge-duplicates'
      },
      body: JSON.stringify(payload)
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}: Gagal menyimpan`);

    // Sync VIP / Developer role status to user_subscriptions
    try {
      const now = new Date();
      const expiryDays = isDev ? 36500 : 365; // 100 Tahun untuk Developer, 1 Tahun untuk Premium
      const newExpiry = new Date(now.getTime() + expiryDays * 24 * 60 * 60 * 1000).toISOString();
      const planTitle = isDev ? 'Developer' : (isPrem ? 'Admin VIP' : 'Gratis');
      const subPayload = {
        user_id: id,
        user_name: name,
        email: email || null,
        plan_name: planTitle,
        price: 0,
        status: isVipOrDev ? 'approved' : 'rejected',
        is_active: isVipOrDev,
        expires_at: isVipOrDev ? newExpiry : null,
        updated_at: new Date().toISOString()
      };
      await fetch(`${appConfig.url}/rest/v1/user_subscriptions`, {
        method: 'POST',
        headers: {
          ...getHeaders(),
          'Prefer': 'resolution=merge-duplicates'
        },
        body: JSON.stringify(subPayload)
      });
    } catch (e) {
      console.warn('Sync Subscriptions error:', e);
    }

    closeModal('userModal');
    showToast('Data pengguna & status VIP berhasil disimpan!', 'success');
    fetchUsers();
    fetchVipSubscriptions();
  } catch (err) {
    console.error(err);
    showToast('Gagal menyimpan: ' + err.message, 'error');
  }
}

async function deleteUser(id, name) {
  if (!confirm(`Apakah Anda yakin ingin menghapus pengguna "${name}" dari database?`)) {
    return;
  }

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/user_stats?id=eq.${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: getHeaders()
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}: Gagal menghapus`);
    showToast(`Pengguna "${name}" berhasil dihapus`, 'success');
    fetchUsers();
  } catch (err) {
    console.error(err);
    showToast('Gagal menghapus: ' + err.message, 'error');
  }
}

// Top 1 Booster Logic
function populateBoostModalUsers() {
  const select = document.getElementById('boostUserSelect');
  if (!select) return;

  select.innerHTML = '<option value="">-- Pilih Akun Anda --</option>' +
    allUsers.map(u => `<option value="${u.id}">${escapeHtml(u.name)} (${formatHours(u.total_listen_ms)})</option>`).join('');
}

function openBoostModal() {
  populateBoostModalUsers();
  openModal('boostModal');
}

function openBoostToMasterModal() {
  populateBoostModalUsers();
  document.getElementById('boostMarginHours').value = '250';
  openModal('boostModal');
}

function boostSingleUser(userId) {
  openBoostModal();
  const select = document.getElementById('boostUserSelect');
  if (select) select.value = userId;
}

async function executeBoostTop1() {
  const userId = document.getElementById('boostUserSelect').value;
  const marginHours = parseFloat(document.getElementById('boostMarginHours').value) || 100;

  if (!userId) {
    showToast('Silakan pilih akun Anda terlebih dahulu', 'error');
    return;
  }

  const user = allUsers.find(u => u.id === userId);
  if (!user) return;

  // Cari skor tertinggi pengguna lain
  const otherUsers = allUsers.filter(u => u.id !== userId);
  const highestOtherMs = otherUsers.length > 0 ? Math.max(...otherUsers.map(u => Number(u.total_listen_ms) || 0)) : 0;
  const highestOtherHours = highestOtherMs / 3600000;

  // Hitung target jam baru
  const newTargetHours = Math.max(highestOtherHours + marginHours, (user.total_listen_ms / 3600000) + marginHours, 160);
  const newTargetMs = hoursToMs(newTargetHours);

  try {
    const payload = {
      total_listen_ms: newTargetMs,
      weekly_listen_ms: Math.max(Number(user.weekly_listen_ms) || 0, hoursToMs(marginHours * 0.5)),
      border_style: user.border_style || 'royal_crown',
      last_updated_at: Date.now()
    };

    const res = await fetch(`${appConfig.url}/rest/v1/user_stats?id=eq.${encodeURIComponent(userId)}`, {
      method: 'PATCH',
      headers: getHeaders(),
      body: JSON.stringify(payload)
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}: Gagal memperbarui`);
    closeModal('boostModal');
    showToast(`Selamat! Akun "${user.name}" sekarang resmi menjadi Peringkat #1 dengan ${Math.round(newTargetHours)} Jam Dengar!`, 'success');
    fetchUsers();
  } catch (err) {
    console.error(err);
    showToast('Gagal melakukan boost: ' + err.message, 'error');
  }
}

// Live Chat & Conversations (v7.0.9)
async function fetchChatConversations() {
  const container = document.getElementById('conversationsList');
  if (!container) return;

  container.innerHTML = '<p class="text-xs text-gray-500 p-4 text-center">Memuat obrolan...</p>';

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/chat_conversations?select=*&order=last_message_at.desc&limit=30`, {
      method: 'GET',
      headers: getHeaders()
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const conversations = await res.json();

    if (conversations.length === 0) {
      container.innerHTML = '<p class="text-xs text-gray-500 p-4 text-center">Belum ada obrolan pengguna yang tercatat.</p>';
      return;
    }

    container.innerHTML = conversations.map(c => {
      const u1 = allUsers.find(u => u.id === c.user1_id)?.name || c.user1_id.slice(0, 8);
      const u2 = allUsers.find(u => u.id === c.user2_id)?.name || c.user2_id.slice(0, 8);
      const dateStr = c.last_message_at ? new Date(c.last_message_at).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' }) : '';

      return `
        <div onclick="openChatMessages('${c.id}', '${escapeHtml(u1)}', '${escapeHtml(u2)}')" class="p-2.5 rounded-lg bg-gray-800/80 hover:bg-gray-700/90 border border-gray-700/60 cursor-pointer transition-colors text-xs">
          <div class="flex items-center justify-between font-bold text-white mb-1">
            <span class="truncate">${escapeHtml(u1)} & ${escapeHtml(u2)}</span>
            <span class="text-[10px] text-gray-500 font-normal">${dateStr}</span>
          </div>
          <p class="text-gray-400 truncate text-[11px]">${escapeHtml(c.last_message || 'Obrolan dimulai...')}</p>
        </div>
      `;
    }).join('');
  } catch (err) {
    container.innerHTML = `<p class="text-xs text-rose-400 p-4 text-center">Gagal memuat obrolan: ${err.message}</p>`;
  }
}

async function openChatMessages(convId, user1Name, user2Name) {
  activeConversationId = convId;
  const header = document.getElementById('activeChatHeader');
  const deleteBtn = document.getElementById('btnDeleteConv');
  const container = document.getElementById('chatMessagesContainer');

  if (header) {
    header.innerHTML = `
      <div class="flex items-center gap-2">
        <span class="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
        <span class="text-sm font-bold text-white">${user1Name} & ${user2Name}</span>
        <span class="text-xs text-gray-500 font-mono">(${convId.slice(0, 8)}...)</span>
      </div>
    `;
  }
  if (deleteBtn) deleteBtn.classList.remove('hidden');

  container.innerHTML = '<p class="text-xs text-gray-500 text-center py-20">Memuat pesan obrolan...</p>';

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/chat_messages?conversation_id=eq.${encodeURIComponent(convId)}&order=created_at.asc&limit=100`, {
      method: 'GET',
      headers: getHeaders()
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const messages = await res.json();

    if (messages.length === 0) {
      container.innerHTML = '<p class="text-xs text-gray-500 text-center py-20">Percakapan ini belum memiliki pesan.</p>';
      return;
    }

    container.innerHTML = messages.map(m => {
      const isMusic = m.message_type === 'music' && m.media_data;
      const media = m.media_data || {};
      const timeStr = m.created_at ? new Date(m.created_at).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' }) : '';

      return `
        <div class="p-3 bg-gray-800 rounded-xl border border-gray-700/60 max-w-lg text-xs space-y-1.5">
          <div class="flex items-center justify-between text-[11px] text-gray-400">
            <span class="font-bold text-blue-400">${escapeHtml(m.sender_id.slice(0, 10))}</span>
            <span class="inline-flex items-center gap-1">${timeStr} ${m.is_read ? '<i class="fa-solid fa-check-double text-blue-400"></i>' : '<i class="fa-solid fa-check text-gray-500"></i>'}</span>
          </div>
          
          ${isMusic ? `
            <div class="p-2.5 bg-gray-900 rounded-lg border border-gray-700 flex items-center gap-3">
              <img src="${media.thumbnail_url || '../icebeats_logo.png'}" class="w-10 h-10 rounded-md object-cover" onerror="this.src='../icebeats_logo.png'">
              <div class="flex-1 min-w-0">
                <div class="font-bold text-white truncate text-xs flex items-center gap-1"><i class="fa-solid fa-music text-purple-400"></i> ${escapeHtml(media.title || 'Lagu')}</div>
                <div class="text-[11px] text-gray-400 truncate">${escapeHtml(media.artist_name || '')}</div>
              </div>
            </div>
          ` : `
            <p class="text-gray-200">${escapeHtml(m.content)}</p>
          `}
        </div>
      `;
    }).join('');

    container.scrollTop = container.scrollHeight;
  } catch (err) {
    container.innerHTML = `<p class="text-xs text-rose-400 text-center py-20">Gagal memuat pesan: ${err.message}</p>`;
  }
}

async function deleteActiveConversation() {
  if (!activeConversationId) return;
  if (!confirm('Hapus seluruh riwayat percakapan ini secara permanen?')) return;

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/chat_conversations?id=eq.${encodeURIComponent(activeConversationId)}`, {
      method: 'DELETE',
      headers: getHeaders()
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    showToast('Percakapan berhasil dihapus', 'success');
    activeConversationId = null;
    document.getElementById('activeChatHeader').innerHTML = '<span class="text-sm font-bold text-white">Pilih obrolan di sebelah kiri</span>';
    document.getElementById('btnDeleteConv').classList.add('hidden');
    document.getElementById('chatMessagesContainer').innerHTML = '<p class="text-xs text-gray-500 text-center py-20">Belum ada obrolan yang dipilih.</p>';
    fetchChatConversations();
  } catch (err) {
    showToast('Gagal menghapus percakapan: ' + err.message, 'error');
  }
}

// Fetch Cloud Library Stats
async function fetchCloudLibraryStats() {
  try {
    const [favs, pls, evs] = await Promise.all([
      fetch(`${appConfig.url}/rest/v1/user_favorites?select=id`, { headers: getHeaders() }),
      fetch(`${appConfig.url}/rest/v1/user_playlists?select=id`, { headers: getHeaders() }),
      fetch(`${appConfig.url}/rest/v1/user_events?select=id`, { headers: getHeaders() })
    ]);

    if (favs.ok) {
      const favData = await favs.json();
      const el = document.getElementById('statFavoritesCount');
      if (el) el.textContent = favData.length.toLocaleString('id-ID');
    }
    if (pls.ok) {
      const plData = await pls.json();
      const el = document.getElementById('statPlaylistsCount');
      if (el) el.textContent = plData.length.toLocaleString('id-ID');
    }
    if (evs.ok) {
      const evData = await evs.json();
      const el = document.getElementById('statEventsCount');
      if (el) el.textContent = evData.length.toLocaleString('id-ID');
    }
  } catch (e) {
    console.error(e);
  }
}

// Modal Helpers
function openModal(modalId) {
  const modal = document.getElementById(modalId);
  if (modal) modal.classList.remove('hidden');
}

function closeModal(modalId) {
  const modal = document.getElementById(modalId);
  if (modal) modal.classList.add('hidden');
}

// Toast System
function showToast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  if (!container) return;

  const toast = document.createElement('div');
  const bg = type === 'success' ? 'bg-emerald-600 text-white' :
             type === 'error' ? 'bg-rose-600 text-white' : 'bg-blue-600 text-white';

  toast.className = `${bg} px-4 py-3 rounded-xl shadow-2xl flex items-center gap-3 text-xs font-semibold max-w-sm animate-bounce`;
  const toastIcon = type === 'success' ? '<i class="fa-solid fa-circle-check text-sm"></i>' :
                    type === 'error' ? '<i class="fa-solid fa-circle-xmark text-sm"></i>' :
                    '<i class="fa-solid fa-circle-info text-sm"></i>';
  toast.innerHTML = `
    <span>${toastIcon}</span>
    <span class="flex-1">${escapeHtml(message)}</span>
  `;

  container.appendChild(toast);
  setTimeout(() => {
    toast.remove();
  }, 4000);
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// ==============================================================================
// LANGGANAN VIP & ACC MANAGEMENT (v7.1.5)
// Real-time Supabase CRUD for public.user_subscriptions
// ==============================================================================

async function fetchVipPendingBadgeCount() {
  try {
    const res = await fetch(`${appConfig.url}/rest/v1/user_subscriptions?status=eq.pending&select=id`, {
      method: 'GET',
      headers: getHeaders()
    });
    if (!res.ok) return;
    const data = await res.json();
    const count = data.length;
    const badge = document.getElementById('sidebarVipPendingCount');
    if (badge) {
      if (count > 0) {
        badge.textContent = count;
        badge.classList.remove('hidden');
      } else {
        badge.classList.add('hidden');
      }
    }
  } catch (e) {
    console.warn('Failed to fetch pending VIP count:', e);
  }
}

async function fetchVipSubscriptions() {
  const tbody = document.getElementById('vipTableBody');
  if (tbody) {
    tbody.innerHTML = `<tr><td colspan="7" class="px-4 py-8 text-center text-gray-400"><div class="animate-pulse">Memuat data langganan dari Supabase...</div></td></tr>`;
  }

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/user_subscriptions?select=*&order=created_at.desc`, {
      method: 'GET',
      headers: getHeaders()
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}: Gagal memuat pesanan langganan`);
    allSubscriptions = await res.json();

    updateVipMetrics();
    applyVipFilters();
  } catch (err) {
    console.error(err);
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="7" class="px-4 py-8 text-center text-rose-400">Gagal memuat data langganan: ${escapeHtml(err.message)}</td></tr>`;
    }
    showToast('Gagal memuat pesanan VIP: ' + err.message, 'error');
  }
}

function updateVipMetrics() {
  const now = new Date();

  // Active VIP
  const activeCount = allSubscriptions.filter(s => {
    if (!s.is_active) return false;
    if (!s.expires_at) return true; // Lifetime
    return new Date(s.expires_at) > now;
  }).length;

  // Pending ACC
  const pendingCount = allSubscriptions.filter(s => s.status === 'pending').length;

  // Total Revenue from approved orders
  const totalRevenue = allSubscriptions
    .filter(s => s.status === 'approved' || s.is_active)
    .reduce((sum, s) => sum + (Number(s.price) || 0), 0);

  const elActive = document.getElementById('statVipActiveCount');
  if (elActive) elActive.textContent = activeCount.toLocaleString('id-ID');

  const elPending = document.getElementById('statVipPendingCount');
  if (elPending) elPending.textContent = pendingCount.toLocaleString('id-ID');

  const elRevenue = document.getElementById('statVipTotalRevenue');
  if (elRevenue) elRevenue.textContent = 'Rp ' + totalRevenue.toLocaleString('id-ID');

  // Sidebar badge
  const sidebarBadge = document.getElementById('sidebarVipPendingCount');
  if (sidebarBadge) {
    if (pendingCount > 0) {
      sidebarBadge.textContent = pendingCount;
      sidebarBadge.classList.remove('hidden');
    } else {
      sidebarBadge.classList.add('hidden');
    }
  }
}

function applyVipFilters() {
  const statusFilter = document.getElementById('vipFilterStatus')?.value || 'all';
  const query = (document.getElementById('vipSearchInput')?.value || '').toLowerCase().trim();

  filteredSubscriptions = allSubscriptions.filter(sub => {
    // Status filter
    if (statusFilter === 'pending' && sub.status !== 'pending') return false;
    if (statusFilter === 'approved' && !(sub.status === 'approved' || sub.is_active)) return false;
    if (statusFilter === 'rejected' && sub.status !== 'rejected') return false;

    // Search query
    if (query) {
      const matchName = (sub.user_name || '').toLowerCase().includes(query);
      const matchEmail = (sub.email || '').toLowerCase().includes(query);
      const matchId = (sub.user_id || '').toLowerCase().includes(query);
      const matchPlan = (sub.plan_name || '').toLowerCase().includes(query);
      if (!matchName && !matchEmail && !matchId && !matchPlan) return false;
    }

    return true;
  });

  renderVipSubscriptionsTable();
}

function renderVipSubscriptionsTable() {
  const tbody = document.getElementById('vipTableBody');
  if (!tbody) return;

  if (filteredSubscriptions.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" class="px-4 py-12 text-center text-gray-500">
          Tidak ada data langganan yang cocok dengan kriteria filter.
        </td>
      </tr>
    `;
    return;
  }

  const now = new Date();

  tbody.innerHTML = filteredSubscriptions.map(sub => {
    const isPending = sub.status === 'pending';
    const isApproved = sub.status === 'approved' || sub.is_active;
    const isExpired = sub.expires_at && new Date(sub.expires_at) < now;

    // Status Badge
    let statusBadge = '';
    if (isPending) {
      statusBadge = `<span class="px-2.5 py-1 text-[11px] font-bold rounded-md bg-amber-950/80 text-amber-300 border border-amber-600/70 inline-flex items-center gap-1.5 animate-pulse"><i class="fa-solid fa-hourglass-half"></i> Menunggu ACC</span>`;
    } else if (isApproved && !isExpired) {
      statusBadge = `<span class="px-2.5 py-1 text-[11px] font-bold rounded-md bg-emerald-950/80 text-emerald-300 border border-emerald-600/70 inline-flex items-center gap-1.5"><i class="fa-solid fa-circle-check text-emerald-400"></i> VIP Aktif</span>`;
    } else if (isExpired) {
      statusBadge = `<span class="px-2.5 py-1 text-[11px] font-bold rounded-md bg-rose-950/80 text-rose-300 border border-rose-600/70 inline-flex items-center gap-1.5"><i class="fa-solid fa-triangle-exclamation text-rose-400"></i> Kadaluarsa</span>`;
    } else {
      statusBadge = `<span class="px-2.5 py-1 text-[11px] font-bold rounded-md bg-gray-800 text-gray-400 border border-gray-700 inline-flex items-center gap-1.5"><i class="fa-solid fa-circle-xmark text-gray-400"></i> Ditolak</span>`;
    }

    // Plan Badge & Styling
    let planBadge = `<span class="px-2 py-0.5 text-xs font-semibold rounded bg-blue-950 text-blue-300 border border-blue-800">${escapeHtml(sub.plan_name || 'VIP')}</span>`;
    if ((sub.plan_name || '').includes('2 Bulan')) {
      planBadge = `<span class="px-2 py-0.5 text-xs font-semibold rounded bg-purple-950 text-purple-300 border border-purple-800">2 Bulan (Populer)</span>`;
    } else if ((sub.plan_name || '').includes('5 Bulan')) {
      planBadge = `<span class="px-2 py-0.5 text-xs font-semibold rounded bg-amber-950 text-amber-300 border border-amber-800">5 Bulan (Hemat)</span>`;
    } else if ((sub.plan_name || '').includes('Tahun') || (sub.plan_name || '').includes('Lifetime')) {
      planBadge = `<span class="px-2 py-0.5 text-xs font-bold rounded bg-yellow-950 text-yellow-300 border border-yellow-700 inline-flex items-center gap-1"><i class="fa-solid fa-crown text-amber-400"></i> ${escapeHtml(sub.plan_name)}</span>`;
    }

    // Formatted Dates
    const orderDate = sub.created_at ? formatDateTimeIndo(sub.created_at) : '-';
    let expiryText = '-';
    if (sub.expires_at) {
      const expDate = new Date(sub.expires_at);
      const diffDays = Math.ceil((expDate - now) / (1000 * 60 * 60 * 24));
      if (diffDays > 0) {
        expiryText = `<span class="text-emerald-400 font-semibold">${formatDateTimeIndo(sub.expires_at)}</span><br><span class="text-[10px] text-gray-400">(${diffDays} hari lagi)</span>`;
      } else {
        expiryText = `<span class="text-rose-400">${formatDateTimeIndo(sub.expires_at)}</span><br><span class="text-[10px] text-rose-500">(Berakhir)</span>`;
      }
    } else if (isApproved) {
      expiryText = `<span class="text-amber-400 font-bold">Selamanya (Lifetime)</span>`;
    }

    const priceFormatted = Number(sub.price) > 0 ? `Rp ${Number(sub.price).toLocaleString('id-ID')}` : 'Gratis / Promo';

    return `
      <tr class="hover:bg-gray-700/30 transition border-b border-gray-700/60">
        <td class="px-4 py-3.5">
          <div class="flex items-center gap-2.5">
            <div class="w-8 h-8 rounded-full bg-amber-900/50 border border-amber-600/40 flex items-center justify-center text-amber-300 font-bold text-xs">
              <i class="fa-solid fa-crown text-amber-400"></i>
            </div>
            <div>
              <div class="font-bold text-white text-xs">${escapeHtml(sub.user_name || 'User Tanpa Nama')}</div>
              <div class="text-[11px] text-gray-400">${escapeHtml(sub.email || '-')}</div>
              <div class="text-[10px] text-gray-500 font-mono mt-0.5 flex items-center gap-1">
                <span>ID: ${escapeHtml(sub.user_id || '')}</span>
                <button onclick="navigator.clipboard.writeText('${sub.user_id}'); showToast('User ID disalin!', 'info')" class="hover:text-amber-300" title="Salin ID"><i class="fa-solid fa-copy"></i></button>
              </div>
            </div>
          </div>
        </td>
        <td class="px-4 py-3.5 whitespace-nowrap">${planBadge}</td>
        <td class="px-4 py-3.5 whitespace-nowrap font-bold text-amber-300">${priceFormatted}</td>
        <td class="px-4 py-3.5 whitespace-nowrap">${statusBadge}</td>
        <td class="px-4 py-3.5 whitespace-nowrap text-xs">${expiryText}</td>
        <td class="px-4 py-3.5 whitespace-nowrap text-gray-400 text-xs">${orderDate}</td>
        <td class="px-4 py-3.5 whitespace-nowrap text-right">
          <div class="flex items-center justify-end gap-1.5">
            ${isPending ? `
              <button onclick="approveVipSubscription('${sub.id}', '${sub.user_id}', '${escapeHtml(sub.plan_name)}')" class="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded shadow flex items-center gap-1" title="ACC dan Aktifkan VIP di HP Pengguna">
                <i class="fa-solid fa-check"></i> ACC
              </button>
              <button onclick="rejectVipSubscription('${sub.id}')" class="px-2 py-1 bg-gray-700 hover:bg-rose-900 text-rose-300 text-xs rounded border border-rose-800 flex items-center gap-1" title="Tolak Pesanan">
                <i class="fa-solid fa-xmark"></i> Tolak
              </button>
            ` : `
              <button onclick="extendVipSubscription('${sub.id}', '${sub.expires_at || ''}', 30)" class="px-2 py-1 bg-blue-600/30 hover:bg-blue-600/50 text-blue-300 text-xs rounded border border-blue-500/40 flex items-center gap-1" title="Tambah Masa Aktif +30 Hari">
                <i class="fa-solid fa-hourglass-half"></i> +30 Hr
              </button>
              ${isApproved ? `
                <button onclick="revokeVipSubscription('${sub.id}', '${sub.user_id}')" class="px-2 py-1 bg-rose-600/20 hover:bg-rose-600/40 text-rose-300 text-xs rounded border border-rose-600/40 flex items-center gap-1" title="Nonaktifkan VIP">
                  <i class="fa-solid fa-stop"></i> Stop
                </button>
              ` : `
                <button onclick="approveVipSubscription('${sub.id}', '${sub.user_id}', '${escapeHtml(sub.plan_name)}')" class="px-2 py-1 bg-emerald-600/20 hover:bg-emerald-600/40 text-emerald-300 text-xs rounded border border-emerald-500/40 flex items-center gap-1" title="Aktifkan Kembali">
                  <i class="fa-solid fa-play"></i> Aktifkan
                </button>
              `}
            `}
            <button onclick="deleteVipSubscription('${sub.id}')" class="p-1 text-gray-500 hover:text-rose-400 rounded" title="Hapus Data Pesanan">
              <i class="fa-solid fa-trash"></i>
            </button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

async function approveVipSubscription(subId, userId, planName) {
  let durationDays = 30;
  if ((planName || '').includes('2 Bulan')) durationDays = 60;
  else if ((planName || '').includes('5 Bulan')) durationDays = 150;
  else if ((planName || '').includes('Tahun')) durationDays = 365;

  const now = new Date();
  const newExpiry = new Date(now.getTime() + durationDays * 24 * 60 * 60 * 1000).toISOString();

  // Coba jalankan via Secure RPC terlebih dahulu
  try {
    const rpcRes = await fetch(`${appConfig.url}/rest/v1/rpc/admin_acc_vip`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({
        p_admin_secret: 'VALORA_VIP_ADMIN_SECURE_2026',
        p_sub_id: subId,
        p_duration_days: durationDays
      })
    });
    if (rpcRes.ok) {
      showToast(`Paket ${planName} BERHASIL DI-ACC secara Aman! VIP aktif di HP pengguna.`, 'success');
      await fetchVipSubscriptions();
      return;
    }
  } catch (rpcErr) {
    console.warn('RPC admin_acc_vip fallback to direct PATCH:', rpcErr);
  }

  // Fallback direct PATCH jika RPC belum diinstal
  const payload = {
    status: 'approved',
    is_active: true,
    expires_at: newExpiry,
    updated_at: new Date().toISOString()
  };

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/user_subscriptions?id=eq.${encodeURIComponent(subId)}`, {
      method: 'PATCH',
      headers: {
        ...getHeaders(),
        'Prefer': 'return=minimal'
      },
      body: JSON.stringify(payload)
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}: Gagal menyetujui langganan`);

    showToast(`Paket ${planName} BERHASIL DI-ACC! Akun VIP aktif otomatis di HP pengguna.`, 'success');
    await fetchVipSubscriptions();
  } catch (err) {
    console.error(err);
    showToast('Gagal me-ACC pesanan: ' + err.message, 'error');
  }
}

async function rejectVipSubscription(subId) {
  if (!confirm('Apakah Anda yakin ingin menolak pesanan ini?')) return;

  // Coba jalankan via Secure RPC
  try {
    const rpcRes = await fetch(`${appConfig.url}/rest/v1/rpc/admin_revoke_vip`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({
        p_admin_secret: 'VALORA_VIP_ADMIN_SECURE_2026',
        p_sub_id: subId
      })
    });
    if (rpcRes.ok) {
      showToast('Pesanan telah ditolak via Secure Gate.', 'info');
      await fetchVipSubscriptions();
      return;
    }
  } catch (rpcErr) {
    console.warn('RPC admin_revoke_vip fallback to direct PATCH:', rpcErr);
  }

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/user_subscriptions?id=eq.${encodeURIComponent(subId)}`, {
      method: 'PATCH',
      headers: {
        ...getHeaders(),
        'Prefer': 'return=minimal'
      },
      body: JSON.stringify({
        status: 'rejected',
        is_active: false,
        updated_at: new Date().toISOString()
      })
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    showToast('Pesanan telah ditolak', 'info');
    await fetchVipSubscriptions();
  } catch (err) {
    console.error(err);
    showToast('Gagal menolak pesanan: ' + err.message, 'error');
  }
}

async function extendVipSubscription(subId, currentExpiresAt, extraDays) {
  const baseTime = (currentExpiresAt && new Date(currentExpiresAt) > new Date())
    ? new Date(currentExpiresAt).getTime()
    : Date.now();
  const newExpiry = new Date(baseTime + extraDays * 24 * 60 * 60 * 1000).toISOString();

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/user_subscriptions?id=eq.${encodeURIComponent(subId)}`, {
      method: 'PATCH',
      headers: {
        ...getHeaders(),
        'Prefer': 'return=minimal'
      },
      body: JSON.stringify({
        expires_at: newExpiry,
        is_active: true,
        status: 'approved',
        updated_at: new Date().toISOString()
      })
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    showToast(`Masa aktif VIP berhasil diperpanjang +${extraDays} hari!`, 'success');
    await fetchVipSubscriptions();
  } catch (err) {
    console.error(err);
    showToast('Gagal memperpanjang masa aktif: ' + err.message, 'error');
  }
}

async function revokeVipSubscription(subId, userId) {
  if (!confirm('Apakah Anda yakin ingin menonaktifkan akun VIP ini? Pengguna akan kembali ke status gratis.')) return;

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/user_subscriptions?id=eq.${encodeURIComponent(subId)}`, {
      method: 'PATCH',
      headers: {
        ...getHeaders(),
        'Prefer': 'return=minimal'
      },
      body: JSON.stringify({
        is_active: false,
        status: 'rejected',
        updated_at: new Date().toISOString()
      })
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    showToast('Akses VIP telah dinonaktifkan', 'info');
    await fetchVipSubscriptions();
  } catch (err) {
    console.error(err);
    showToast('Gagal menonaktifkan VIP: ' + err.message, 'error');
  }
}

async function deleteVipSubscription(subId) {
  if (!confirm('Apakah Anda yakin ingin menghapus riwayat pesanan VIP ini secara permanen?')) return;

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/user_subscriptions?id=eq.${encodeURIComponent(subId)}`, {
      method: 'DELETE',
      headers: getHeaders()
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    showToast('Riwayat pesanan VIP dihapus', 'success');
    await fetchVipSubscriptions();
  } catch (err) {
    console.error(err);
    showToast('Gagal menghapus pesanan: ' + err.message, 'error');
  }
}

// Manual VIP Grant Modal
function openManualVipModal() {
  const select = document.getElementById('manualVipUserSelect');
  if (select) {
    select.innerHTML = '<option value="">-- Pilih dari Pengguna Terdaftar --</option>';
    allUsers.forEach(u => {
      const opt = document.createElement('option');
      opt.value = u.id;
      opt.textContent = `${u.name || 'User'} (${u.email || u.id})`;
      select.appendChild(opt);
    });
  }

  document.getElementById('manualVipUserId').value = '';
  document.getElementById('manualVipUserName').value = '';
  document.getElementById('manualVipUserEmail').value = '';
  openModal('manualVipModal');
}

function onManualVipUserSelected() {
  const select = document.getElementById('manualVipUserSelect');
  const userId = select.value;
  if (!userId) return;

  const user = allUsers.find(u => u.id === userId);
  if (user) {
    document.getElementById('manualVipUserId').value = user.id;
    document.getElementById('manualVipUserName').value = user.name || '';
    document.getElementById('manualVipUserEmail').value = user.email || '';
  }
}

async function submitManualVip() {
  const userId = document.getElementById('manualVipUserId').value.trim();
  const userName = document.getElementById('manualVipUserName').value.trim();
  const email = document.getElementById('manualVipUserEmail').value.trim();
  const planSelect = document.getElementById('manualVipPlanSelect');
  const selectedOpt = planSelect.options[planSelect.selectedIndex];

  if (!userId) {
    showToast('User ID tidak boleh kosong!', 'error');
    return;
  }

  const planName = planSelect.value;
  const days = parseInt(selectedOpt.getAttribute('data-days')) || 30;
  const price = parseInt(selectedOpt.getAttribute('data-price')) || 0;

  const now = new Date();
  const newExpiry = new Date(now.getTime() + days * 24 * 60 * 60 * 1000).toISOString();

  // 1. Coba eksekusi via Secure RPC (Bypass RLS dengan Secret Key)
  try {
    const rpcRes = await fetch(`${appConfig.url}/rest/v1/rpc/admin_grant_manual_vip`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({
        p_admin_secret: 'VALORA_VIP_ADMIN_SECURE_2026',
        p_user_id: userId,
        p_user_name: userName || 'VIP Member',
        p_email: email || null,
        p_plan_name: planName,
        p_duration_days: days
      })
    });
    if (rpcRes.ok) {
      closeModal('manualVipModal');
      showToast(`Akses VIP (${planName}) BERHASIL diberikan ke ${userName || userId}!`, 'success');
      await fetchVipSubscriptions();
      return;
    }
  } catch (rpcErr) {
    console.warn('RPC admin_grant_manual_vip fallback to direct POST:', rpcErr);
  }

  const payload = {
    user_id: userId,
    user_name: userName || 'VIP Member',
    email: email || null,
    plan_name: planName,
    price: price,
    status: 'approved',
    is_active: true,
    expires_at: newExpiry,
    updated_at: new Date().toISOString()
  };

  try {
    const res = await fetch(`${appConfig.url}/rest/v1/user_subscriptions`, {
      method: 'POST',
      headers: {
        ...getHeaders(),
        'Prefer': 'resolution=merge-duplicates'
      },
      body: JSON.stringify(payload)
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}: Gagal menyimpan VIP`);

    closeModal('manualVipModal');
    showToast(`Akses VIP (${planName}) berhasil diberikan ke ${userName || userId}!`, 'success');
    await fetchVipSubscriptions();
  } catch (err) {
    console.error(err);
    showToast('Gagal mengaktifkan VIP: ' + err.message, 'error');
  }
}

function formatDateTimeIndo(isoString) {
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;
    return d.toLocaleDateString('id-ID', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  } catch (e) {
    return isoString;
  }
}

