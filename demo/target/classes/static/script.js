/* ============ Role & Nav Config ============ */
const roles = {
  teacher: {
    name: "王老師", avatar: "王",
    nav: [
      {id:"t-dash", icon:"ti-layout-dashboard", label:"儀表板"},
      {id:"t-import", icon:"ti-upload", label:"題庫匯入"},
      {id:"t-bank", icon:"ti-folder", label:"題庫管理"},
      {id:"t-folder", icon:"ti-files", label:"學習資料夾"},
      {id:"t-paper", icon:"ti-clipboard-list", label:"固定試卷"},
      {id:"t-analysis", icon:"ti-chart-bar", label:"成效分析"},
      {id:"t-perm", icon:"ti-users", label:"小老師權限"},
      {id:"t-announce", icon:"ti-speakerphone", label:"課程公告"},
      {id:"t-course", icon:"ti-school", label:"課程管理"},
    ],
    default:"t-dash"
  },
  assistant: {
    name: "李小老師", avatar: "李",
    nav: [
      {id:"a-dash", icon:"ti-layout-dashboard", label:"儀表板"},
      {id:"a-import", icon:"ti-upload", label:"題庫匯入", locked:true},
      {id:"a-bank", icon:"ti-folder", label:"題庫管理", locked:true},
      {id:"a-folder", icon:"ti-files", label:"跨文件統整測驗", locked:true},
      {id:"a-announce", icon:"ti-speakerphone", label:"課程公告"},
    ],
    default:"a-dash"
  },
  student: {
    name: "陳同學", avatar: "陳",
    nav: [
      {id:"s-home", icon:"ti-home", label:"首頁"},
      {id:"s-quiz", icon:"ti-pencil", label:"開始測驗"},
      {id:"s-weak", icon:"ti-target-arrow", label:"弱點分析"},
      {id:"s-wrong", icon:"ti-repeat", label:"錯題本"},
      {id:"s-remind", icon:"ti-clock", label:"複習提醒"},
      {id:"s-analysis", icon:"ti-chart-line", label:"學習成效分析"},
      {id:"s-bankinfo", icon:"ti-database", label:"題庫資訊"},
      {id:"s-record", icon:"ti-history", label:"自我評量紀錄"},
      {id:"s-announce", icon:"ti-speakerphone", label:"課程公告"},
    ],
    default:"s-home"
  },
  admin: {
    name: "系統管理員", avatar: "管",
    nav: [
      {id:"m-dash", icon:"ti-layout-dashboard", label:"儀表板"},
      {id:"m-account", icon:"ti-user-cog", label:"帳號管理"},
      {id:"m-perm", icon:"ti-shield-lock", label:"權限設定"},
      {id:"m-announce", icon:"ti-speakerphone", label:"系統公告"},
      {id:"m-usage", icon:"ti-chart-donut", label:"系統用量"},
      {id:"m-log", icon:"ti-alert-triangle", label:"錯誤紀錄"},
      {id:"m-course", icon:"ti-school", label:"課程資料管理"},
    ],
    default:"m-dash"
  }
};

/* ============ Render sidebar ============ */
function renderSidebar(roleKey){
  const role = roles[roleKey];
  const sidebar = document.getElementById('sidebar');
  sidebar.innerHTML = '<div class="side-section-label">功能選單</div>' +
    role.nav.map(item => `
      <div class="nav-item" data-panel="${item.id}">
        <i class="ti ${item.icon}"></i>
        <span>${item.label}</span>
        ${item.locked ? '<span class="nav-note">需授權</span>' : ''}
      </div>
    `).join('');

  sidebar.querySelectorAll('.nav-item').forEach(el=>{
    el.addEventListener('click', ()=> showPanel(el.dataset.panel));
  });
}

function showPanel(panelId){
  document.querySelectorAll('.nav-item').forEach(el=>{
    el.classList.toggle('active', el.dataset.panel === panelId);
  });
  document.querySelectorAll('.panel').forEach(el=>{
    el.classList.toggle('active', el.id === panelId);
  });
}

const roleLabel = {teacher:"老師", assistant:"小老師", student:"學生", admin:"管理者"};

function switchRole(roleKey){
  document.querySelectorAll('.role-btn').forEach(b=>{
    b.classList.toggle('active', b.dataset.role === roleKey);
  });
  const role = roles[roleKey];
  document.getElementById('userName').textContent = role.name;
  document.getElementById('userAvatar').textContent = role.avatar;
  renderSidebar(roleKey);
  showPanel(role.default);

  const pName = document.getElementById('profileName');
  const pAvatar = document.getElementById('profileAvatar');
  const pRole = document.getElementById('profileRole');
  if(pName){ pName.textContent = role.name; pAvatar.textContent = role.avatar; pRole.textContent = roleLabel[roleKey]; }
}

document.getElementById('roleSwitcher').addEventListener('click', e=>{
  const btn = e.target.closest('.role-btn');
  if(btn) switchRole(btn.dataset.role);
});

/* ============ Profile page ============ */
document.getElementById('userChip').addEventListener('click', ()=>{
  document.querySelectorAll('.nav-item').forEach(el=>el.classList.remove('active'));
  document.querySelectorAll('.panel').forEach(el=>el.classList.remove('active'));
  const profilePanel = document.getElementById('profile-panel');
  if (profilePanel) profilePanel.classList.add('active');
});

/* ============ Login ============ */
let loginRole = 'teacher';
document.getElementById('loginRoleSelect').addEventListener('click', e=>{
  const btn = e.target.closest('button');
  if(!btn) return;
  document.querySelectorAll('#loginRoleSelect button').forEach(b=>b.classList.toggle('active', b===btn));
  loginRole = btn.dataset.role;
});

// 這裡是重點：替換為呼叫 Java API 的邏輯
document.getElementById('loginSubmit').addEventListener('click', async () => {
  const inputs = document.querySelectorAll('.login-field input');
  const usernameInput = inputs[0].value;
  const passwordInput = inputs[1].value;
  
  if (!usernameInput || !passwordInput) {
    alert("請輸入帳號與密碼！");
    return;
  }

  const loginBtn = document.getElementById('loginSubmit');
  const originalBtnText = loginBtn.innerHTML;
  loginBtn.innerHTML = '<i class="ti ti-loader"></i> 登入中...';
  loginBtn.disabled = true;

  try {
    const response = await fetch('http://localhost:8080/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        username: usernameInput,
        password: passwordInput,
        role: loginRole 
      })
    });

    const result = await response.json();

    if (result.success) {
        alert(result.message + '！歡迎，' + result.fullName);
        
        roles[loginRole].name = result.fullName;
        roles[loginRole].avatar = result.fullName.charAt(0);

        document.getElementById('loginScreen').style.display = 'none';
        document.getElementById('mainApp').style.display = 'flex';
        switchRole(loginRole);
    } else {
        alert(result.message);
    }
  } catch (error) {
    console.error('連線失敗:', error);
    alert("無法連線到伺服器，請確認 Java 後端是否已啟動！");
  } finally {
    loginBtn.innerHTML = originalBtnText;
    loginBtn.disabled = false;
  }
});

/* ============ Exam System ============ */
const startBtn = document.getElementById('startExamBtn');
if (startBtn) {
    startBtn.addEventListener('click', ()=> showPanel('s-taking'));
}

document.querySelectorAll('.exam-option').forEach(opt=>{
  opt.addEventListener('click', ()=>{
    opt.closest('.exam-options').querySelectorAll('.exam-option').forEach(o=>o.classList.remove('selected'));
    opt.classList.add('selected');
  });
});

/* init */
// switchRole('teacher'); 已經註解掉，確保系統一開始停在登入畫面