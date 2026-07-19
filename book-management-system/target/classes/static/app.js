let me = null;
let categories = [];

const $ = (id) => document.getElementById(id);
const api = async (url, options = {}) => {
  const res = await fetch(url, {headers: {'Content-Type': 'application/json'}, ...options});
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || '请求失败');
  return data;
};
const post = (url, body) => api(url, {method: 'POST', body: JSON.stringify(body)});

function toast(message, error = false) {
  $('toast').innerHTML = `<div class="toast ${error ? 'error' : ''}">${message}</div>`;
  setTimeout(() => $('toast').innerHTML = '', 2600);
}

async function init() {
  bindEvents();
  try { me = await api('/api/me'); showApp(); } catch { showLogin(); }
}

function bindEvents() {
  $('loginBtn').onclick = login;
  $('regBtn').onclick = register;
  $('logoutBtn').onclick = logout;
  $('searchBtn').onclick = loadBooks;
  $('askBtn').onclick = askAi;
  $('descBtn').onclick = genDesc;
  $('addCatBtn').onclick = addCategory;
  $('saveBookBtn').onclick = saveBook;
  document.querySelectorAll('nav button').forEach(btn => btn.onclick = () => showTab(btn.dataset.tab));
}

async function login() {
  try {
    me = await post('/api/auth/login', {username: $('loginUsername').value, password: $('loginPassword').value});
    showApp();
  } catch (e) { toast(e.message, true); }
}

async function register() {
  try {
    me = await post('/api/auth/register', {username: $('regUsername').value, displayName: $('regName').value, password: $('regPassword').value});
    showApp();
  } catch (e) { toast(e.message, true); }
}

async function logout() {
  await post('/api/auth/logout', {});
  me = null;
  showLogin();
}

function showLogin() {
  $('loginView').classList.remove('hidden');
  $('appView').classList.add('hidden');
  $('userText').textContent = '未登录';
}

async function showApp() {
  $('loginView').classList.add('hidden');
  $('appView').classList.remove('hidden');
  $('userText').textContent = `${me.DISPLAY_NAME || me.display_name}（${roleText(me.ROLE || me.role)}）`;
  document.querySelectorAll('.admin-only').forEach(el => el.style.display = (me.ROLE === 'ADMIN' || me.role === 'ADMIN') ? '' : 'none');
  await loadCategories();
  await loadBooks();
  showTab('books');
}

function showTab(id) {
  document.querySelectorAll('.page').forEach(p => p.classList.add('hidden'));
  $(id).classList.remove('hidden');
  if (id === 'records') loadRecords();
  if (id === 'ai') loadRecommend();
  if (id === 'admin') loadDashboard();
}

async function loadCategories() {
  categories = await api('/api/categories');
  $('bookCategory').innerHTML = categories.map(c => `<option value="${c.ID}">${c.NAME}</option>`).join('');
  $('categoryList').innerHTML = categories.map(c => `<span class="chip">${c.NAME}: ${c.DESCRIPTION || ''}</span>`).join('');
}

async function loadBooks() {
  const keyword = encodeURIComponent($('keyword').value || '');
  const books = await api(`/api/books?keyword=${keyword}`);
  $('bookList').innerHTML = books.map(b => `<article class="card">
    <span class="tag">${b.CATEGORY_NAME}</span>
    <h2>${b.TITLE}</h2>
    <p>${b.AUTHOR} · ${b.PUBLISHER || ''}</p>
    <p class="muted">${b.DESCRIPTION || ''}</p>
    <p>ISBN: ${b.ISBN}</p>
    <div class="card-foot"><span>库存 ${b.AVAILABLE_STOCK}/${b.TOTAL_STOCK}</span><div>
      <button onclick="borrowBook(${b.ID})" ${b.AVAILABLE_STOCK <= 0 ? 'disabled' : ''}>借阅</button>
      ${(me.ROLE === 'ADMIN' || me.role === 'ADMIN') ? `<button class="ghost" onclick='editBook(${JSON.stringify(b)})'>编辑</button>` : ''}
    </div></div>
  </article>`).join('') || '<p class="muted">暂无图书。</p>';
}

async function borrowBook(id) {
  try { await post(`/api/borrow/${id}`, {}); toast('借阅成功'); await loadBooks(); }
  catch (e) { toast(e.message, true); }
}

async function loadRecords() {
  try {
    const rows = await api('/api/records');
    $('recordRows').innerHTML = rows.map(r => `<tr><td>${r.DISPLAY_NAME}</td><td>${r.TITLE}</td><td>${fmt(r.BORROWED_AT)}</td><td>${fmt(r.DUE_AT)}</td><td><span class="status">${statusText(r.STATUS)}</span></td><td>${r.STATUS === 'BORROWED' ? `<button onclick="returnBook(${r.ID})">归还</button>` : ''}</td></tr>`).join('') || '<tr><td colspan="6">暂无借阅记录。</td></tr>';
  } catch (e) { toast(e.message, true); }
}

async function returnBook(id) {
  try { await post(`/api/return/${id}`, {}); toast('归还成功'); await loadRecords(); }
  catch (e) { toast(e.message, true); }
}

async function askAi() {
  const question = $('question').value.trim();
  if (!question) {
    $('answer').textContent = '请先输入问题，例如：逾期怎么办？';
    return;
  }
  $('answer').textContent = '正在思考...';
  try {
    const data = await post('/api/ai/ask', {question});
    const source = data.source === 'llm' ? '真实大模型' : '真实大模型暂不可用，已使用本地后备方案';
    const reason = data.reason ? `\n\n原因：${data.reason}` : '';
    $('answer').textContent = `[${source}] ${data.answer || '没有返回回答。'}${reason}`;
  } catch (e) {
    $('answer').textContent = 'AI 请求失败：' + e.message;
    toast(e.message, true);
  }
}


async function loadRecommend() {
  try {
    const list = await api('/api/ai/recommend');
    $('recommendList').innerHTML = list.map(b => `<li><strong>${b.TITLE}</strong><span>${b.CATEGORY_NAME}</span></li>`).join('');
  } catch (e) { toast(e.message, true); }
}

async function genDesc() {
  const data = await post('/api/ai/description', {title: $('aiTitle').value, author: $('aiAuthor').value, category: $('aiCategory').value});
  $('generatedDesc').textContent = data.description;
}

async function loadDashboard() {
  try {
    const d = await api('/api/dashboard');
    $('stats').innerHTML = [
      ['图书数量', d.bookCount], ['读者数量', d.readerCount], ['借阅中', d.borrowedCount], ['可借种类', d.availableKinds]
    ].map(x => `<div class="stat"><strong>${x[1]}</strong><span>${x[0]}</span></div>`).join('');
    await loadCategories();
  } catch (e) { toast(e.message, true); }
}

async function addCategory() {
  try { await post('/api/categories', {name: $('catName').value, description: $('catDesc').value}); toast('分类已新增'); $('catName').value=''; $('catDesc').value=''; await loadCategories(); }
  catch (e) { toast(e.message, true); }
}

function editBook(b) {
  showTab('admin');
  $('bookId').value = b.ID;
  $('bookTitle').value = b.TITLE;
  $('bookAuthor').value = b.AUTHOR;
  $('bookIsbn').value = b.ISBN;
  $('bookPublisher').value = b.PUBLISHER || '';
  $('bookTotal').value = b.TOTAL_STOCK;
  $('bookAvailable').value = b.AVAILABLE_STOCK;
  $('bookCategory').value = b.CATEGORY_ID;
  $('bookDesc').value = b.DESCRIPTION || '';
}

async function saveBook() {
  try {
    await post('/api/books', {
      id: $('bookId').value, title: $('bookTitle').value, author: $('bookAuthor').value, isbn: $('bookIsbn').value,
      publisher: $('bookPublisher').value, totalStock: $('bookTotal').value, availableStock: $('bookAvailable').value,
      categoryId: $('bookCategory').value, description: $('bookDesc').value
    });
    toast('图书已保存');
    ['bookId','bookTitle','bookAuthor','bookIsbn','bookPublisher','bookTotal','bookAvailable','bookDesc'].forEach(id => $(id).value = '');
    await loadBooks();
  } catch (e) { toast(e.message, true); }
}

function fmt(v) { return v ? String(v).replace('T', ' ').slice(0, 19) : ''; }
function statusText(status) {
  if (status === 'BORROWED') return '借阅中';
  if (status === 'RETURNED') return '已归还';
  return status || '';
}
function roleText(role) {
  if (role === 'ADMIN') return '管理员';
  if (role === 'READER') return '读者';
  return role || '';
}
window.addEventListener('DOMContentLoaded', init);
