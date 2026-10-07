/**
 * ==============================================================================
 * ICEBEATS ADMIN DASHBOARD - FLOWBITE CONTROLLER (v7.0.9)
 * Full CRUD, Top 1 Booster, Live Chat Inspector & Master Border Management
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
let currentPage = 1;
const pageSize = 15;
let activeConversationId = null;

// Initialize on Load
document.addEventListener('DOMContentLoaded', () => {
  loadStoredConfig();
  setupEventListeners();
  populateBadgePickers();
  fetchUsers();
  fetchCloudLibraryStats();
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
  if (tabId === 'chatTab') {
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
      'royal_crown': '👑 Royal Crown',
      'crimson_wing': '🪽 Crimson Wings',
      'fire_flame': '🔥 Fire Flame',
      'golden_shield': '🛡️ Golden Champion'
    };
    const userBorderBadge = isMasterPlus
      ? `<div class="text-[10px] text-amber-300/90 font-medium mt-0.5">${borderLabels[u.border_style] || '👑 Royal Crown'}</div>`
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
              ${isMasterPlus ? '<span class="absolute -top-1 -right-1 text-xs">👑</span>' : ''}
            </div>
            <div>
              <div class="font-bold text-white text-sm">${escapeHtml(u.name || 'User')}</div>
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
              🚀 Top 1
            </button>
            <button onclick="openEditUserModal('${u.id}')" class="px-2 py-1 bg-blue-600/20 hover:bg-blue-600/30 text-blue-300 text-xs rounded border border-blue-500/40 font-medium">
              ✏️ Edit
            </button>
            <button onclick="deleteUser('${u.id}', '${escapeHtml(u.name)}')" class="px-2 py-1 bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 text-xs rounded border border-rose-500/40 font-medium">
              🗑️ Hapus
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
    closeModal('userModal');
    showToast('Data pengguna berhasil diperbarui!', 'success');
    fetchUsers();
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
            <span>${timeStr} ${m.is_read ? '✓✓' : '✓'}</span>
          </div>
          
          ${isMusic ? `
            <div class="p-2.5 bg-gray-900 rounded-lg border border-gray-700 flex items-center gap-3">
              <img src="${media.thumbnail_url || '../icebeats_logo.png'}" class="w-10 h-10 rounded-md object-cover" onerror="this.src='../icebeats_logo.png'">
              <div class="flex-1 min-w-0">
                <div class="font-bold text-white truncate text-xs">🎵 ${escapeHtml(media.title || 'Lagu')}</div>
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
  toast.innerHTML = `
    <span>${type === 'success' ? '✅' : type === 'error' ? '❌' : 'ℹ️'}</span>
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
