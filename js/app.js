/* ============================================================
   SECTION 1: IN-MEMORY DATABASE (Empty default, loads from backend DB)
   ============================================================ */
let defaultCourses = [
  {
    id: 1,
    name: "高二基礎物理",
    code: "PHY-201",
    type: "必修",
    year: "113",
    semester: "1",
    credits: "3",
    grade: "高二",
    classGroup: "甲班",
    teacher: "王老師",
    students: [
      { id: 1, studentNo: "S1130101", name: "張廷瑋", seatNo: "01", email: "s1130101@school.edu.tw", note: "物理小老師" },
      { id: 2, studentNo: "S1130102", name: "林語晨", seatNo: "02", email: "s1130102@school.edu.tw", note: "" },
      { id: 3, studentNo: "S1130103", name: "陳冠宇", seatNo: "03", email: "s1130103@school.edu.tw", note: "" },
      { id: 4, studentNo: "S1130104", name: "黃品瑄", seatNo: "04", email: "s1130104@school.edu.tw", note: "" },
      { id: 5, studentNo: "S1130105", name: "趙韋翔", seatNo: "05", email: "s1130105@school.edu.tw", note: "" }
    ],
    createdAt: "2026-08-20"
  },
  {
    id: 2,
    name: "進階程式設計與演算法",
    code: "CS-102",
    type: "選修",
    year: "113",
    semester: "1",
    credits: "2",
    grade: "高二",
    classGroup: "綜合班",
    teacher: "王老師",
    students: [
      { id: 1, studentNo: "S1130201", name: "李承恩", seatNo: "01", email: "s1130201@school.edu.tw", note: "" },
      { id: 2, studentNo: "S1130202", name: "趙偉捷", seatNo: "02", email: "s1130202@school.edu.tw", note: "" },
      { id: 3, studentNo: "S1130203", name: "許雅婷", seatNo: "03", email: "s1130203@school.edu.tw", note: "" }
    ],
    createdAt: "2026-08-22"
  }
];

let savedCourses = null;
try {
  const c = localStorage.getItem('qb_courses');
  if (c) savedCourses = JSON.parse(c);
} catch (e) { }

let DB = {
  nextQId: 1,
  nextPId: 1,
  nextFId: 1,
  nextMId: 1,
  questions: [],
  pending: [],
  folders: [],
  courses: savedCourses || defaultCourses
};

function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

/* ============================================================
   SECTION 2: API LAYER (USE_API=false → in-memory fallback)
   ============================================================ */
const API_BASE = 'http://localhost:8080/api';
let USE_API = true; //已開啟 Spring Boot API 連線

async function apiFetch(method, path, body) {
  const res = await fetch(API_BASE + path, {
    method, headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

const QuestionAPI = {
  async list(filters = {}) {
    if (USE_API) {
      const res = await apiFetch('GET', '/questions?' + new URLSearchParams(filters));
      const rawList = (res && res.data) ? res.data : (Array.isArray(res) ? res : []);
      const mapped = rawList.map(q => ({
        id: q.id,
        content: q.content,
        optA: q.optA || q.optionA || '',
        optB: q.optB || q.optionB || '',
        optC: q.optC || q.optionC || '',
        optD: q.optD || q.optionD || '',
        answer: q.answer || 'A',
        subject: q.subject || '未分類',
        unit: q.unit || '未分類',
        dept: q.dept || q.department || '自然科學科',
        difficulty: q.difficulty || '中',
        source: q.source || q.sourceType || '教師手動'
      }));
      if (!filters || Object.values(filters).every(v => !v)) {
        DB.questions = mapped;
        syncDashboard();
      }
      return mapped;
    }
    let list = [...DB.questions];

    if (filters.dept) list = list.filter(q => q.dept === filters.dept);
    if (filters.subject) list = list.filter(q => q.subject === filters.subject);
    if (filters.unit) list = list.filter(q => q.unit === filters.unit);
    if (filters.difficulty) list = list.filter(q => q.difficulty === filters.difficulty);
    if (filters.keyword) list = list.filter(q => q.content.includes(filters.keyword) || q.optA.includes(filters.keyword) || q.optB.includes(filters.keyword));
    return list;
  },
  async create(data) {
    if (USE_API) return apiFetch('POST', '/questions', data);
    const q = { ...data, id: DB.nextQId++ };
    DB.questions.push(q);
    syncDashboard();
    return q;
  },
  async update(id, data) {
    if (USE_API) return apiFetch('PUT', '/questions/' + id, data);
    const idx = DB.questions.findIndex(q => q.id === id);
    if (idx >= 0) DB.questions[idx] = { ...DB.questions[idx], ...data };
    return DB.questions[idx];
  },
  async remove(id) {
    if (USE_API) return apiFetch('DELETE', '/questions/' + id);
    DB.questions = DB.questions.filter(q => q.id !== id);
    syncDashboard();
    return true;
  }
};

function getSavedGeminiApiKey() {
  const inputH = document.getElementById('geminiApiKeyInputHeader')?.value?.trim();
  const inputP = document.getElementById('geminiApiKeyInput')?.value?.trim();
  return inputH || inputP || localStorage.getItem('gemini_api_key') || '';
}

function saveGeminiApiKey(key) {
  key = key.trim();
  localStorage.setItem('gemini_api_key', key);
  const inputH = document.getElementById('geminiApiKeyInputHeader');
  const inputP = document.getElementById('geminiApiKeyInput');
  if (inputH) inputH.value = key;
  if (inputP) inputP.value = key;
  if (key) {
    toast('Google Gemini API Key 已成功儲存！', 'success');
  } else {
    toast('已清除 API Key', 'info');
  }
}

function toggleApiKeyVisibility() {
  const input = document.getElementById('geminiApiKeyInput');
  const btn = document.getElementById('eyeIcon');
  if (input) {
    if (input.type === 'password') {
      input.type = 'text';
      if (btn) btn.className = 'ti ti-eye-off';
    } else {
      input.type = 'password';
      if (btn) btn.className = 'ti ti-eye';
    }
  }
}

const ImportAPI = {
  async listPending() {
    if (USE_API) return apiFetch('GET', '/import/pending');
    return DB.pending.filter(p => p.status === 'pending');
  },
  async uploadFixed(files) {
    if (USE_API) {
      const formData = new FormData();
      files.forEach(f => formData.append('files', f));
      const headers = {};
      const key = getSavedGeminiApiKey();
      if (key) headers['X-Gemini-Api-Key'] = key;
      const res = await fetch(API_BASE + '/import/fixed', { method: 'POST', headers, body: formData });
      const data = await res.json();
      if (!res.ok || (data && data.success === false)) {
        throw new Error((data && data.message) ? data.message : '固定格式檔案解析失敗！');
      }
      return data;
    }
    return null;
  },
  async uploadPptAi(files, subject, difficulty, count) {
    if (USE_API) {
      const formData = new FormData();
      files.forEach(f => formData.append('files', f));
      if (subject) formData.append('subject', subject);
      if (difficulty) formData.append('difficulty', difficulty);
      if (count) formData.append('count', count);
      const headers = {};
      const key = getSavedGeminiApiKey();
      if (key) headers['X-Gemini-Api-Key'] = key;
      const res = await fetch(API_BASE + '/import/ppt-ai', { method: 'POST', headers, body: formData });
      const data = await res.json();
      if (!res.ok || (data && data.success === false)) {
        throw new Error((data && data.message) ? data.message : 'PPT AI 智慧出題失敗！');
      }
      return data;
    }
    return null;
  },
  async confirm(id) {
    if (USE_API) return apiFetch('PUT', '/import/' + id + '/confirm');
    const p = DB.pending.find(p => p.id === id);
    if (p) {
      p.status = 'confirmed';
      DB.questions.push({
        id: DB.nextQId++, content: p.content, optA: p.optA, optB: p.optB, optC: p.optC, optD: p.optD,
        answer: p.answer, subject: p.subject || '未分類', unit: p.unit || '未分類', dept: '自然科學科', difficulty: '中', source: p.source || 'AI匯入'
      });
      syncDashboard();
    }
    return true;
  },
  async confirmAll() {
    if (USE_API) return apiFetch('POST', '/import/confirm-all');
    const pendingList = DB.pending.filter(p => p.status === 'pending');
    pendingList.forEach(p => {
      p.status = 'confirmed';
      DB.questions.push({
        id: DB.nextQId++, content: p.content, optA: p.optA, optB: p.optB, optC: p.optC, optD: p.optD,
        answer: p.answer, subject: p.subject || '未分類', unit: p.unit || '未分類', dept: '自然科學科', difficulty: '中', source: p.source || 'AI匯入'
      });
    });
    syncDashboard();
    return pendingList.length;
  },
  async update(id, data) {
    if (USE_API) return apiFetch('PUT', '/import/' + id, data);
    const idx = DB.pending.findIndex(p => p.id === id);
    if (idx >= 0) DB.pending[idx] = { ...DB.pending[idx], ...data };
    return DB.pending[idx];
  },
  async remove(id) {
    if (USE_API) return apiFetch('DELETE', '/import/' + id);
    DB.pending = DB.pending.filter(p => p.id !== id);
    return true;
  }
};

const FolderAPI = {
  async list() {
    if (USE_API) {
      const res = await apiFetch('GET', '/folders');
      const rawList = (res && res.data) ? res.data : (Array.isArray(res) ? res : []);
      return rawList.map(f => ({
        id: f.id,
        name: f.name,
        materials: (f.materials || []).map(m => ({ id: m.id, name: m.fileName || m.name, type: m.fileType || m.type })),
        generated: f.generated || [],
        generatedCount: f.generatedCount || (f.generated ? f.generated.length : 0)
      }));
    }
    return DB.folders;
  },
  async create(name) {
    if (USE_API) {
      const res = await apiFetch('POST', '/folders', { name });
      const f = (res && res.data) ? res.data : res;
      return { id: f.id, name: f.name, materials: [], generated: [], generatedCount: 0 };
    }
    const f = { id: DB.nextFId++, name, materials: [], generated: [], generatedCount: 0 };
    DB.folders.push(f);
    return f;
  },
  async remove(id) {
    if (USE_API) return apiFetch('DELETE', '/folders/' + id);
    DB.folders = DB.folders.filter(f => f.id !== id);
    return true;
  },
  async addMaterial(folderId, file, name, type) {
    if (USE_API && (file instanceof File || file instanceof Blob)) {
      const formData = new FormData();
      formData.append('file', file);
      const res = await fetch(API_BASE + '/folders/' + folderId + '/materials', { method: 'POST', body: formData });
      const json = await res.json();
      const m = (json && json.data) ? json.data : json;
      return { id: m.id, name: m.fileName || file.name, type: m.fileType || getFileType(file.name) };
    }
    const f = DB.folders.find(f => f.id === folderId);
    if (f) {
      const m = { id: DB.nextMId++, name: name || (file ? file.name : '新檔案'), type: type || (file ? getFileType(file.name) : 'PDF') };
      f.materials.push(m);
      return m;
    }
  },
  async removeMaterial(folderId, matId) {
    if (USE_API) return apiFetch('DELETE', '/folders/' + folderId + '/materials/' + matId);
    const f = DB.folders.find(f => f.id === folderId);
    if (f) f.materials = f.materials.filter(m => m.id !== matId);
    return true;
  },
  async generate(folderId) {
    if (USE_API) {
      const res = await apiFetch('POST', '/folders/' + folderId + '/generate');
      return (res && res.data) ? res.data : (Array.isArray(res) ? res : []);
    }
    return new Promise(resolve => {
      setTimeout(() => {
        const folder = DB.folders.find(f => f.id === folderId);
        const qs = buildGeneratedQuestions(folder);
        folder.generated = qs;
        folder.generatedCount = qs.length;
        resolve(qs);
      }, 3500);
    });
  },
  async importGenerated(folderId, q) {
    if (USE_API) return apiFetch('POST', '/folders/' + folderId + '/questions/' + q.gid + '/import', q);
    DB.questions.push({
      id: DB.nextQId++, content: q.content, optA: q.optA, optB: q.optB, optC: q.optC, optD: q.optD,
      answer: q.answer, subject: q.subject, unit: q.chapter, dept: '自然科學科', difficulty: '難', source: 'AI統整'
    });
    syncDashboard();
    return true;
  }
};

const CourseAPI = {
  async list() {
    if (USE_API) {
      try {
        const res = await apiFetch('GET', '/courses');
        const rawList = (res && res.data) ? res.data : (Array.isArray(res) ? res : []);
        const mapped = rawList.map(c => ({
          id: c.id,
          name: c.name,
          code: c.code,
          type: c.type || '必修',
          year: c.year || '113',
          semester: c.semester || '1',
          credits: c.credits || '3',
          grade: c.grade || '高二',
          classGroup: c.classGroup || '甲班',
          teacher: c.teacher || '王老師',
          createdAt: c.createdAt || '',
          students: (c.students || []).map(s => ({
            id: s.id,
            studentNo: s.studentNo,
            name: s.name,
            seatNo: s.seatNo,
            email: s.email,
            note: s.note,
            isAssistant: !!s.isAssistant,
            assistantPermissions: s.assistantPermissions || ''
          }))
        }));
        DB.courses = mapped;
        return mapped;
      } catch (e) {
        console.warn('無法連線至後端課程 API，使用本地快取', e);
      }
    }
    return DB.courses || [];
  },
  async create(courseData) {
    if (USE_API) {
      const payload = {
        name: courseData.name,
        code: courseData.code || ('CRS-' + String(Date.now()).slice(-6)),
        type: courseData.type || '必修',
        year: courseData.year || '113',
        semester: courseData.semester || '1',
        credits: courseData.credits || '3',
        grade: courseData.grade || '高二',
        classGroup: courseData.classGroup || '忠班',
        teacher: courseData.teacher || '王大明',
        students: (courseData.students || []).map(s => ({
          studentNo: s.studentNo,
          name: s.name,
          seatNo: s.seatNo || '',
          email: s.email || '',
          note: s.note || '',
          isAssistant: !!s.isAssistant,
          assistantPermissions: s.assistantPermissions || ''
        }))
      };
      const res = await apiFetch('POST', '/courses', payload);
      const saved = (res && res.data) ? res.data : res;
      return {
        id: saved.id || Date.now(),
        name: saved.name,
        code: saved.code,
        type: saved.type || '必修',
        year: saved.year || '113',
        semester: saved.semester || '1',
        credits: saved.credits || '3',
        grade: saved.grade || '高二',
        classGroup: saved.classGroup || '忠班',
        teacher: saved.teacher || '王大明',
        createdAt: saved.createdAt || new Date().toISOString().split('T')[0],
        students: (saved.students || []).map(s => ({
          id: s.id,
          studentNo: s.studentNo,
          name: s.name,
          seatNo: s.seatNo,
          email: s.email,
          note: s.note,
          isAssistant: !!s.isAssistant,
          assistantPermissions: s.assistantPermissions || ''
        }))
      };
    }
    return { ...courseData, id: Date.now(), createdAt: new Date().toISOString().split('T')[0] };
  },
  async update(id, courseData) {
    if (USE_API) {
      const payload = {
        name: courseData.name,
        code: courseData.code,
        type: courseData.type,
        year: courseData.year,
        semester: courseData.semester,
        credits: courseData.credits,
        grade: courseData.grade,
        classGroup: courseData.classGroup,
        teacher: courseData.teacher,
        students: (courseData.students || []).map(s => ({
          studentNo: s.studentNo,
          name: s.name,
          seatNo: s.seatNo || '',
          email: s.email || '',
          note: s.note || '',
          isAssistant: !!s.isAssistant,
          assistantPermissions: s.assistantPermissions || ''
        }))
      };
      const res = await apiFetch('PUT', '/courses/' + id, payload);
      const saved = (res && res.data) ? res.data : res;
      return {
        id: saved.id || id,
        name: saved.name,
        code: saved.code,
        type: saved.type || '必修',
        year: saved.year || '113',
        semester: saved.semester || '1',
        credits: saved.credits || '3',
        grade: saved.grade || '高二',
        classGroup: saved.classGroup || '忠班',
        teacher: saved.teacher || '王大明',
        createdAt: saved.createdAt,
        students: (saved.students || []).map(s => ({
          id: s.id,
          studentNo: s.studentNo,
          name: s.name,
          seatNo: s.seatNo,
          email: s.email,
          note: s.note,
          isAssistant: !!s.isAssistant,
          assistantPermissions: s.assistantPermissions || ''
        }))
      };
    }
    return { ...courseData, id };
  },
  async remove(id) {
    if (USE_API) {
      return apiFetch('DELETE', '/courses/' + id);
    }
    return true;
  },
  async getStudents(courseId) {
    if (USE_API) {
      try {
        const res = await apiFetch('GET', `/courses/${courseId}/students`);
        return (res && res.data) ? res.data : (Array.isArray(res) ? res : []);
      } catch (e) {
        console.warn('讀取學生名單失敗', e);
      }
    }
    const c = (DB.courses || []).find(x => x.id === courseId);
    return c ? (c.students || []) : [];
  }
};

const UserAPI = {
  async getStudents() {
    if (USE_API) {
      try {
        const res = await apiFetch('GET', '/users/students');
        return (res && res.data) ? res.data : (Array.isArray(res) ? res : []);
      } catch (e) {
        console.warn('讀取使用者學生資料失敗', e);
      }
    }
    return [
      { id: 1, username: 'S1130101', name: '王曉明', FullName: '王曉明', GradeLevel: '一年', ClassName: 'A班', displayName: 'A班王曉明', email: 's1130101@school.edu.tw', seatNo: '01' },
      { id: 2, username: 'S1130102', name: '林語晨', FullName: '林語晨', GradeLevel: '一年', ClassName: 'A班', displayName: 'A班林語晨', email: 's1130102@school.edu.tw', seatNo: '02' },
      { id: 3, username: 'S1130103', name: '陳冠宇', FullName: '陳冠宇', GradeLevel: '一年', ClassName: 'A班', displayName: 'A班陳冠宇', email: 's1130103@school.edu.tw', seatNo: '03' },
      { id: 4, username: 'S1130104', name: '黃品瑄', FullName: '黃品瑄', GradeLevel: '一年', ClassName: 'B班', displayName: 'B班黃品瑄', email: 's1130104@school.edu.tw', seatNo: '04' },
      { id: 5, username: 'S1130105', name: '趙韋翔', FullName: '趙韋翔', GradeLevel: '一年', ClassName: 'B班', displayName: 'B班趙韋翔', email: 's1130105@school.edu.tw', seatNo: '05' }
    ];
  },
  async verifyStudents(students) {
    if (USE_API) {
      try {
        const payload = (students || []).map(s => ({
          studentNo: s.studentNo,
          name: s.name,
          seatNo: s.seatNo,
          email: s.email,
          note: s.note
        }));
        const res = await apiFetch('POST', '/users/verify-students', payload);
        if (res && res.data) return res.data;
      } catch (e) {
        console.warn('API 驗證學生失敗，使用本地快取比對', e);
      }
    }
    const dbStudents = await UserAPI.getStudents();
    let verifiedCount = 0, unverifiedCount = 0;
    const verifiedList = (students || []).map((s, idx) => {
      const sNo = (s.studentNo || '').trim().toLowerCase();
      const sName = (s.name || '').trim();
      const match = dbStudents.find(u =>
        (sNo && u.username && u.username.toLowerCase() === sNo) ||
        (sName && u.name && u.name === sName)
      );
      if (match) {
        verifiedCount++;
        return {
          id: s.id || (Date.now() + idx),
          studentNo: match.username,
          name: match.name,
          seatNo: s.seatNo || match.seatNo || '',
          email: match.email || s.email || '',
          note: s.note || '',
          verified: true,
          userId: match.id,
          statusMessage: `已核對到系統學生 (${match.username} / ${match.name})`
        };
      } else {
        unverifiedCount++;
        return {
          id: s.id || (Date.now() + idx),
          studentNo: s.studentNo,
          name: s.name,
          seatNo: s.seatNo || '',
          email: s.email || '',
          note: s.note || '',
          verified: false,
          statusMessage: '資料庫 user 表無此帳號'
        };
      }
    });
    return {
      totalCount: students.length,
      verifiedCount,
      unverifiedCount,
      students: verifiedList
    };
  }
};

function buildGeneratedQuestions(folder) {
  const subjects = folder.materials.map(m => m.name).join('、');
  const banks = [
    {
      gid: 'g1', content: `在「${folder.name}」的學習資料中，若一帶電粒子在均勻電場中做等加速度直線運動，則該粒子所受電場力 F 與加速度 a 的關係符合下列哪項描述？`,
      optA: 'F 與 a 成正比（F=ma），方向相同', optB: 'F 與 a 成反比，方向相反', optC: 'F 與 a 無關，電場力為常數', optD: 'F 只與電場強度有關，與質量無關',
      answer: 'A', chapter: '力學 × 電磁學', subject: folder.materials[0]?.name.includes('生物') || folder.name.includes('生物') ? '生物' : '物理', added: false
    },
    {
      gid: 'g2', content: `綜合${folder.name}各章節概念：波動現象在電磁場中傳播時，光的速度 c 與電場常數 ε₀ 及磁場常數 μ₀ 之關係為何？`,
      optA: 'c = 1/√(ε₀μ₀)', optB: 'c = ε₀ × μ₀', optC: 'c = √(ε₀/μ₀)', optD: 'c = ε₀ + μ₀',
      answer: 'A', chapter: '電磁學 × 波動光學', subject: folder.name.includes('生物') ? '生物' : '物理', added: false
    },
    {
      gid: 'g3', content: `依據${folder.name}之綜合複習：下列哪項敘述同時正確描述了力學與波動的共同特性？`,
      optA: '兩者均服從能量守恆定律', optB: '力學中動量不守恆，但波動中能量守恆', optC: '只有力學有干涉現象', optD: '波動不具備動量',
      answer: 'A', chapter: '力學 × 波動光學', subject: folder.name.includes('生物') ? '生物' : '物理', added: false
    },
    {
      gid: 'g4', content: `根據「${folder.name}」中多章節的交叉比對，在分析細胞膜電位變化時，需要結合哪兩個核心概念？`,
      optA: '離子通道（生物電）× 電場理論（物理）', optB: '細胞分裂 × 波動傳播', optC: '遺傳密碼 × 力學平衡', optD: '酵素催化 × 熱力學第二定律',
      answer: 'A', chapter: '跨科目統整', subject: folder.name.includes('生物') ? '生物' : '物理', added: false
    },
  ];
  return banks.slice(0, 3);
}

/* ============================================================
   SECTION 3: MODAL SYSTEM
   ============================================================ */
const Modal = {
  _onConfirm: null,
  open({ title, body, confirmText = '確認', cancelText = '取消', onConfirm, hideFooter = false, confirmClass = 'primary' }) {
    document.getElementById('modalTitle').textContent = title;
    document.getElementById('modalBody').innerHTML = body;
    document.getElementById('modalConfirmBtn').textContent = confirmText;
    document.getElementById('modalConfirmBtn').className = confirmClass === 'danger' ? 'danger' : 'primary';
    document.getElementById('modalFooter').style.display = hideFooter ? 'none' : '';
    document.getElementById('modalCancelBtn').textContent = cancelText;
    this._onConfirm = onConfirm;
    document.getElementById('modalOverlay').classList.add('active');
  },
  close() {
    document.getElementById('modalOverlay').classList.remove('active');
    this._onConfirm = null;
  },
  showError(title, message, detail = '') {
    this.open({
      title: title || '⚠️ 系統處理發生錯誤',
      body: `
        <div style="text-align:center;padding:12px 0;">
          <div style="font-size:46px;margin-bottom:12px;color:#dc2626;"><i class="ti ti-alert-circle"></i></div>
          <div style="font-size:15px;font-weight:600;color:var(--ink);margin-bottom:8px;">${message}</div>
          ${detail ? `<div style="font-size:12.5px;color:#991b1b;background:#fef2f2;padding:10px 14px;border-radius:8px;border:1px solid #fecaca;text-align:left;word-break:break-all;max-height:160px;overflow-y:auto;font-family:monospace;margin-top:12px;">${detail}</div>` : ''}
        </div>
      `,
      confirmText: '確定',
      hideFooter: false,
      confirmClass: 'danger',
      onConfirm: () => Modal.close()
    });
  },
  getVal(id) { return document.getElementById(id)?.value?.trim(); }
};
document.getElementById('modalClose').onclick = () => Modal.close();
document.getElementById('modalCancelBtn').onclick = () => Modal.close();
document.getElementById('modalConfirmBtn').onclick = () => { if (Modal._onConfirm) Modal._onConfirm(); };
document.getElementById('modalOverlay').addEventListener('click', e => { if (e.target === e.currentTarget) Modal.close(); });

/* ============================================================
   SECTION 4: TOAST SYSTEM
   ============================================================ */
function toast(msg, type = 'success') {
  const icons = { success: 'ti-check-circle', error: 'ti-circle-x', warning: 'ti-alert-triangle' };
  const el = document.createElement('div');
  el.className = `toast-msg ${type}`;
  el.innerHTML = `<i class="ti ${icons[type] || icons.success}"></i><span>${msg}</span>`;
  document.getElementById('toastContainer').appendChild(el);
  requestAnimationFrame(() => { setTimeout(() => el.classList.add('show'), 10); });
  setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 350); }, 2800);
}

/* ============================================================
   SECTION 5: DASHBOARD SYNC
   ============================================================ */
function syncDashboard() {
  const total = DB.questions.length;
  const pendingCount = DB.pending.filter(p => p.status === 'pending').length;
  const el1 = document.getElementById('dash-total');
  const el2 = document.getElementById('dash-pending');
  if (el1) el1.textContent = total.toLocaleString();
  if (el2) { el2.textContent = pendingCount; }

  // 動態更新題庫單元分佈
  const unitContainer = document.getElementById('dashUnitDistribution');
  if (unitContainer) {
    if (total === 0) {
      unitContainer.innerHTML = `<div style="text-align:center;padding:24px;color:var(--ink-mute);font-size:13px;">
        <i class="ti ti-database-off" style="font-size:24px;display:block;margin-bottom:6px;color:var(--ink-mute);"></i>
        資料庫目前尚無題目資料。請至「題庫匯入」上傳試卷或「題庫管理」新增題目。
      </div>`;
    } else {
      const unitMap = {};
      DB.questions.forEach(q => {
        const u = q.unit || '未分類單元';
        unitMap[u] = (unitMap[u] || 0) + 1;
      });
      const colors = ['var(--teal-400)', '#4285F4', '#F2A623', '#9C27B0', '#009688', '#FF5722', '#3F51B5'];
      let idx = 0;
      let html = '';
      for (const [unitName, count] of Object.entries(unitMap)) {
        const pct = Math.round((count / total) * 100);
        const color = colors[idx % colors.length];
        html += `<div class="weak-bar-row">
          <div class="unit-name" title="${unitName}">${unitName}</div>
          <div class="weak-bar-track"><div class="weak-bar-fill" style="width:${pct}%;background:${color}"></div></div>
          <div class="weak-bar-pct" style="min-width:68px;text-align:right;font-weight:500;">${count} 題 (${pct}%)</div>
        </div>`;
        idx++;
      }
      unitContainer.innerHTML = html;
    }
  }

  // Update pending badge in step
  const bc = document.getElementById('import-pending-badge');
  if (bc) { bc.textContent = pendingCount; bc.style.display = pendingCount > 0 ? '' : 'none'; }
}


/* ============================================================
   SECTION 6: DUAL-MODE IMPORT PANEL (固定格式 + Google Gemini PPT AI 擷取)
   ============================================================ */
let selectedFiles = [];
let currentImportMode = 'fixed'; // 'fixed' | 'ppt-ai'

async function initImportPanel() {
  // Mode 1: Fixed Format Drop Zone
  const zoneFixed = document.getElementById('importDropZoneFixed');
  const inputFixed = document.getElementById('importFileInputFixed');
  if (zoneFixed && inputFixed) {
    zoneFixed.addEventListener('dragover', e => { e.preventDefault(); zoneFixed.classList.add('dragover'); });
    zoneFixed.addEventListener('dragleave', () => zoneFixed.classList.remove('dragover'));
    zoneFixed.addEventListener('drop', e => {
      e.preventDefault(); zoneFixed.classList.remove('dragover');
      handleFiles([...e.dataTransfer.files]);
    });
    inputFixed.addEventListener('change', () => { handleFiles([...inputFixed.files]); inputFixed.value = ''; });
  }

  // Mode 2: PPT AI Drop Zone
  const zonePpt = document.getElementById('importDropZonePpt');
  const inputPpt = document.getElementById('importFileInputPpt');
  if (zonePpt && inputPpt) {
    zonePpt.addEventListener('dragover', e => { e.preventDefault(); zonePpt.classList.add('dragover'); });
    zonePpt.addEventListener('dragleave', () => zonePpt.classList.remove('dragover'));
    zonePpt.addEventListener('drop', e => {
      e.preventDefault(); zonePpt.classList.remove('dragover');
      handleFiles([...e.dataTransfer.files]);
    });
    inputPpt.addEventListener('change', () => { handleFiles([...inputPpt.files]); inputPpt.value = ''; });
  }

  const keyInput = document.getElementById('geminiApiKeyInput');
  if (keyInput) keyInput.value = getSavedGeminiApiKey();

  if (USE_API) {
    try {
      const res = await ImportAPI.listPending();
      if (res && res.data) DB.pending = res.data;
    } catch (e) {
      console.warn('載入待確認題目清單失敗', e);
    }
  }

  renderPendingTable();
  syncDashboard();
}

function switchImportMode(mode) {
  currentImportMode = mode;
  selectedFiles = [];
  renderFileList();

  const tabFixed = document.getElementById('tabFixedImport');
  const tabPpt = document.getElementById('tabPptAiImport');
  const areaFixed = document.getElementById('fixedImportArea');
  const areaPpt = document.getElementById('pptAiImportArea');

  if (mode === 'fixed') {
    tabFixed?.classList.add('active'); tabPpt?.classList.remove('active');
    if (areaFixed) areaFixed.style.display = 'block';
    if (areaPpt) areaPpt.style.display = 'none';
  } else {
    tabPpt?.classList.add('active'); tabFixed?.classList.remove('active');
    if (areaPpt) areaPpt.style.display = 'block';
    if (areaFixed) areaFixed.style.display = 'none';
  }
}

function downloadExcelTemplate() {
  const csvContent = "\uFEFF" +
    "題目內容,選項A,選項B,選項C,選項D,正確答案,科目,單元,難度\n" +
    "下列何者為牛頓第二運動定律的公式？,F=ma,F=mv,F=m/a,F=a/m,A,物理,力學,易\n" +
    "細胞膜的主要成分是什麼？,磷脂雙層,蛋白質,纖維素,澱粉,A,生物,細胞學,易\n" +
    "苯 (benzene) 的化學分子式為何？,C6H6,C6H12,C6H14,C6H5OH,A,化學,有機化學,易\n";
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = '標準題庫匯入範本_Excel.csv';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  toast('已下載標準 Excel 題庫範本', 'success');
}

function downloadPdfTemplate() {
  const pdfGuide = "=== 智慧題庫系統 — 標準 PDF 試卷排版範本指南 ===\n\n" +
    "1. 下列何者為牛頓第二運動定律的公式？\n" +
    "   (A) F=ma\n   (B) F=mv\n   (C) F=m/a\n   (D) F=a/m\n" +
    "   解答：A\n\n" +
    "2. 細胞膜的主要成分是什麼？\n" +
    "   (A) 磷脂雙層\n   (B) 蛋白質\n   (C) 纖維素\n   (D) 澱粉\n" +
    "   解答：A\n";
  const blob = new Blob([pdfGuide], { type: 'text/plain;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = '標準題庫排版範本_PDF說明.txt';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  toast('已下載 PDF 排版範本指南', 'success');
}

function getFileType(name) {
  const ext = name.split('.').pop().toLowerCase();
  if (['pdf'].includes(ext)) return 'PDF';
  if (['xlsx', 'xls', 'csv'].includes(ext)) return 'Excel';
  if (['pptx', 'ppt'].includes(ext)) return 'PPT';
  if (['docx', 'doc'].includes(ext)) return 'Word';
  if (['png', 'jpg', 'jpeg'].includes(ext)) return '圖片';
  return '檔案';
}
function getFileIcon(type) {
  const map = { PDF: 'ti-file-type-pdf', Excel: 'ti-file-type-xls', PPT: 'ti-file-type-ppt', Word: 'ti-file-type-doc', 圖片: 'ti-photo' };
  return map[type] || 'ti-file';
}
function getFileIconColor(type) {
  const map = { PDF: '#1565c0', Excel: '#2e7d32', PPT: '#d83b01', Word: '#283593', 圖片: '#6a1b9a' };
  return map[type] || 'var(--ink-mute)';
}

function handleFiles(files) {
  files.forEach(f => {
    if (!selectedFiles.find(sf => sf.name === f.name)) {
      selectedFiles.push(f);
    }
  });
  renderFileList();
}

function renderFileList() {
  const el = document.getElementById('importFileList');
  const actions = document.getElementById('importActionBar');
  if (!selectedFiles.length) { el.innerHTML = ''; el.style.display = 'none'; actions.style.display = 'none'; return; }
  el.style.display = 'flex';
  actions.style.display = 'flex';
  el.innerHTML = selectedFiles.map((f, i) => {
    const type = getFileType(f.name);
    const icon = getFileIcon(type);
    const color = getFileIconColor(type);
    const size = (f.size / 1024).toFixed(1) + ' KB';
    return `<div class="file-item">
      <i class="ti ${icon} fi-icon" style="color:${color}"></i>
      <span class="fi-name">${f.name}</span>
      <span class="fi-size">${size}</span>
      <i class="ti ti-x fi-remove" onclick="removeFile(${i})"></i>
    </div>`;
  }).join('');
}

function removeFile(idx) {
  selectedFiles.splice(idx, 1);
  renderFileList();
}

async function processSelectedImport() {
  if (!selectedFiles.length) { toast('請先選擇要匯入的檔案', 'warning'); return; }
  const btn = document.getElementById('startImportBtn');
  btn.disabled = true;
  const progressDiv = document.getElementById('importProgressArea');
  progressDiv.style.display = 'block';
  const progressItems = document.getElementById('importProgressItems');
  progressItems.innerHTML = '';

  const filesToProcess = [...selectedFiles];
  filesToProcess.forEach(f => {
    const modeLabel = currentImportMode === 'ppt-ai' ? 'Google Gemini AI 解析中...' : '固定格式驗證中...';
    progressItems.innerHTML += `<div class="progress-item" id="prog-${f.name.replace(/\W/g, '_')}">
      <div class="pi-label"><span><i class="ti ti-file-text"></i> ${f.name} (${modeLabel})</span><span id="pct-${f.name.replace(/\W/g, '_')}">0%</span></div>
      <div class="progress-track"><div class="progress-fill" id="fill-${f.name.replace(/\W/g, '_')}" style="width:0%"></div></div>
    </div>`;
  });

  let apiSuccess = false;
  if (USE_API) {
    try {
      if (currentImportMode === 'fixed') {
        await ImportAPI.uploadFixed(filesToProcess);
      } else {
        const subj = document.getElementById('pptImportSubject')?.value || '一般';
        const diff = document.getElementById('pptImportDifficulty')?.value || '中';
        const count = parseInt(document.getElementById('pptImportQuestionCount')?.value) || 5;
        await ImportAPI.uploadPptAi(filesToProcess, subj, diff, count);
      }
      const pendingRes = await ImportAPI.listPending();
      if (pendingRes && pendingRes.data) {
        DB.pending = pendingRes.data;
        apiSuccess = true;
      }
    } catch (err) {
      console.error('API 處理失敗', err);
      btn.disabled = false;
      progressDiv.style.display = 'none';
      Modal.showError('解析與擷取失敗', '系統在處理檔案或呼叫 Google Gemini API 時發生錯誤：', err.message || err);
      return;
    }
  }

  let completed = 0;
  filesToProcess.forEach((f, i) => {
    const key = f.name.replace(/\W/g, '_');
    let pct = 0;
    const interval = setInterval(() => {
      pct += Math.random() * 25 + 10;
      if (pct >= 100) {
        pct = 100; clearInterval(interval);
        document.getElementById('fill-' + key)?.classList.add('done');
        completed++;
        if (completed === filesToProcess.length) {
          setTimeout(() => {
            if (!apiSuccess) {
              // 僅在非 API 模式下建立模擬演練資料
              addSimulatedImportRecords(filesToProcess, currentImportMode);
            } else {
              renderPendingTable();
              syncDashboard();
            }
            btn.disabled = false;
            progressDiv.style.display = 'none';
            selectedFiles = [];
            renderFileList();
            switchImportStep('review');
            const totalPending = DB.pending.filter(p => p.status === 'pending').length;
            if (apiSuccess) {
              toast(`解析成功！後端 API 已為您匯入 ${totalPending} 筆真實題目與解答`, 'success');
            } else {
              toast(`目前為前端預覽模式。已載入 ${totalPending} 筆範例項目。請啟動 Spring Boot 後端即可解析真實 PDF/PPT！`, 'info');
            }
          }, 600);
        }
      }
      const fillEl = document.getElementById('fill-' + key);
      const pctEl = document.getElementById('pct-' + key);
      if (fillEl) fillEl.style.width = Math.min(pct, 100) + '%';
      if (pctEl) pctEl.textContent = Math.round(Math.min(pct, 100)) + '%';
    }, 80 + i * 30);
  });
}

function addSimulatedImportRecords(files, mode) {
  const subj = document.getElementById('pptImportSubject')?.value || '物理';
  const diff = document.getElementById('pptImportDifficulty')?.value || '中';

  // 清除系統初始預設之測試資料（避免與使用者剛剛上傳的檔案混合）
  DB.pending = DB.pending.filter(p => !['物理講義ch3.pdf', '光學單元.xlsx'].includes(p.source));

  files.forEach(f => {
    const isPdf = f.name.toLowerCase().endsWith('.pdf');
    if (mode === 'ppt-ai') {
      // Google Gemini AI PPT Extraction Templates
      const pptTemplates = [
        {
          content: `（Gemini AI 從 ${f.name} 投影片 3 擷取）本簡報所論述的核心概念與原理為何？`,
          optA: '主要定理公式與關鍵定義（簡報重點）',
          optB: '次要補充細節與變數說明',
          optC: '非相關理論反例',
          optD: '未在簡報中提及之推論',
          answer: 'A', confidence: 95, unit: '簡報核心章節'
        },
        {
          content: `（Gemini AI 從 ${f.name} 投影片 7 擷取）根據簡報圖表數據，下列哪項實驗推論最正確？`,
          optA: '圖表呈現之正相關主要結論',
          optB: '異常離群值之誤判說明',
          optC: '反向因果關係推論',
          optD: '變因控制失敗之假設',
          answer: 'A', confidence: 91, unit: '簡報實驗分析'
        },
        {
          content: `（Gemini AI 綜合簡報總結生成）關於本章簡報範例之應用限制，下列何者符合投影片備忘錄說明？`,
          optA: '須於理想環境條件下成立',
          optB: '適用於無限制之任何條件',
          optC: '僅能用於微觀量子尺度',
          optD: '完全不具實務應用價值',
          answer: 'A', confidence: 93, unit: '簡報綜合應用'
        }
      ];
      pptTemplates.forEach(t => {
        DB.pending.push({
          id: DB.nextPId++, content: t.content, optA: t.optA, optB: t.optB, optC: t.optC, optD: t.optD,
          answer: t.answer, subject: subj, unit: t.unit, source: f.name,
          confidence: t.confidence, status: 'pending'
        });
      });
    } else {
      // Fixed Format Import Templates (PDF vs Excel distinction)
      const fixedTemplates = isPdf ? [
        {
          content: `（從 ${f.name} 內文第 1 頁解析）下列何者為該試卷單元規範要求之基本單位？`,
          optA: '國際標準單位 SI（正確）', optB: '英制單位', optC: '導出單位', optD: '無因次量',
          answer: 'A', confidence: 94, subject: 'PDF題目', unit: '第一單元'
        },
        {
          content: `（從 ${f.name} 內文第 2 頁解析）根據內文定理分析，下列關於主要變因之敘述何者正確？`,
          optA: '變因 A 與變因 B 呈正相關（符合本文論述）', optB: '變因 A 與變因 B 呈反比', optC: '兩者無統計顯著差異', optD: '本文未提及此觀念',
          answer: 'A', confidence: 92, subject: 'PDF題目', unit: '第二單元'
        }
      ] : [
        {
          content: `（來自 ${f.name} 欄位 A1）下列何者為該單元標準規範要求之基本單位？`,
          optA: '國際標準單位 SI（正確）', optB: '英制單位', optC: '導出單位', optD: '無因次量',
          answer: 'A', confidence: 98, subject: 'Excel題目', unit: '表格第一列'
        },
        {
          content: `（來自 ${f.name} 欄位 A2）根據標準匯入表格，關於系統運作之正確選項為何？`,
          optA: '精確符合表格欄位對應', optB: '欄位錯位', optC: '型態不符', optD: '缺失欄位',
          answer: 'A', confidence: 97, subject: 'Excel題目', unit: '表格第二列'
        }
      ];
      fixedTemplates.forEach(t => {
        DB.pending.push({
          id: DB.nextPId++, content: t.content, optA: t.optA, optB: t.optB, optC: t.optC, optD: t.optD,
          answer: t.answer, subject: t.subject, unit: t.unit, source: f.name,
          confidence: t.confidence, status: 'pending'
        });
      });
    }
  });
  renderPendingTable();
  syncDashboard();
}

function switchImportStep(step) {
  const uploadSection = document.getElementById('importUploadSection');
  const reviewSection = document.getElementById('importReviewSection');
  const stepUpload = document.getElementById('importStepUpload');
  const stepReview = document.getElementById('importStepReview');
  if (step === 'upload') {
    uploadSection.style.display = 'block'; reviewSection.style.display = 'none';
    stepUpload.classList.add('active'); stepReview.classList.remove('active');
  } else {
    uploadSection.style.display = 'none'; reviewSection.style.display = 'block';
    stepUpload.classList.remove('active'); stepReview.classList.add('active');
    renderPendingTable();
  }
}

let pendingPage = 1;
let pendingPageSize = 10;

function changePendingPageSize(sizeStr) {
  pendingPageSize = parseInt(sizeStr) || 10;
  pendingPage = 1;
  renderPendingTable();
}

function goToPendingPage(p) {
  pendingPage = p;
  renderPendingTable();
}

function renderPendingTable() {
  const tbody = document.getElementById('pendingTbody');
  const countEl = document.getElementById('pendingCountLabel');
  if (!tbody) return;
  const list = DB.pending.filter(p => p.status === 'pending');
  const total = list.length;

  if (countEl) countEl.textContent = '共 ' + total + ' 筆待確認題目';
  const bc = document.getElementById('import-pending-badge');
  if (bc) { bc.textContent = total; bc.style.display = total > 0 ? '' : 'none'; }

  const bar = document.getElementById('pendingPaginationBar');

  if (!total) {
    tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;color:var(--ink-mute);padding:32px;">
      <i class="ti ti-check-all" style="font-size:28px;color:var(--teal-400);display:block;margin-bottom:8px;"></i>
      目前沒有待確認的題目。請點擊上方「選擇匯入模式與檔案」上傳題目。
    </td></tr>`;
    if (bar) bar.style.display = 'none';
    return;
  }

  if (bar) bar.style.display = 'flex';

  // 計算分頁
  const totalPages = Math.max(1, Math.ceil(total / pendingPageSize));
  if (pendingPage > totalPages) pendingPage = totalPages;
  if (pendingPage < 1) pendingPage = 1;

  const startIndex = (pendingPage - 1) * pendingPageSize;
  const endIndex = Math.min(startIndex + pendingPageSize, total);
  const slicedList = list.slice(startIndex, endIndex);

  // 更新顯示範圍文字
  const rangeEl = document.getElementById('pendingPageRangeText');
  if (rangeEl) {
    if (pendingPageSize >= 9000) {
      rangeEl.textContent = `顯示全部 ${total} 筆題目`;
    } else {
      rangeEl.textContent = `顯示第 ${startIndex + 1} - ${endIndex} 筆，共 ${total} 筆`;
    }
  }

  // 更新頁碼按鈕列
  const navEl = document.getElementById('pendingPageNav');
  if (navEl) {
    if (totalPages <= 1 || pendingPageSize >= 9000) {
      navEl.innerHTML = '';
    } else {
      let navHtml = '';
      navHtml += `<button class="ghost" style="padding:4px 8px;font-size:12px;" ${pendingPage === 1 ? 'disabled' : ''} onclick="goToPendingPage(${pendingPage - 1})"><i class="ti ti-chevron-left"></i> 上一頁</button>`;

      let startP = Math.max(1, pendingPage - 2);
      let endP = Math.min(totalPages, startP + 4);
      if (endP - startP < 4) startP = Math.max(1, endP - 4);

      if (startP > 1) {
        navHtml += `<button class="ghost" style="padding:4px 8px;font-size:12px;" onclick="goToPendingPage(1)">1</button>`;
        if (startP > 2) navHtml += `<span style="color:var(--ink-mute);padding:0 2px;">...</span>`;
      }

      for (let p = startP; p <= endP; p++) {
        if (p === pendingPage) {
          navHtml += `<button class="primary" style="padding:4px 10px;font-size:12px;font-weight:600;">${p}</button>`;
        } else {
          navHtml += `<button class="ghost" style="padding:4px 10px;font-size:12px;" onclick="goToPendingPage(${p})">${p}</button>`;
        }
      }

      if (endP < totalPages) {
        if (endP < totalPages - 1) navHtml += `<span style="color:var(--ink-mute);padding:0 2px;">...</span>`;
        navHtml += `<button class="ghost" style="padding:4px 8px;font-size:12px;" onclick="goToPendingPage(${totalPages})">${totalPages}</button>`;
      }

      navHtml += `<button class="ghost" style="padding:4px 8px;font-size:12px;" ${pendingPage === totalPages ? 'disabled' : ''} onclick="goToPendingPage(${pendingPage + 1})">下一頁 <i class="ti ti-chevron-right"></i></button>`;
      navEl.innerHTML = navHtml;
    }
  }

  tbody.innerHTML = slicedList.map(p => {
    const content = p.content || '';
    const optA = p.optA || p.optionA || '';
    const optB = p.optB || p.optionB || '';
    const optC = p.optC || p.optionC || '';
    const optD = p.optD || p.optionD || '';
    const source = p.source || p.sourceFile || '檔案';
    const conf = p.confidence || 90;
    const confBadgeClass = conf >= 92 ? 'confidence-high' : conf >= 80 ? 'confidence-mid' : 'confidence-low';
    const isPpt = source.endsWith('.pptx') || source.endsWith('.ppt');
    const isPdf = source.endsWith('.pdf');
    return `<tr>
      <td>
        <div style="font-weight:500;color:var(--ink);margin-bottom:4px;" title="${content}">${content}</div>
        <div style="font-size:11.5px;color:var(--ink-mute);display:flex;gap:10px;">
          <span>(A) ${optA}</span>
          <span>(B) ${optB}</span>
          <span>(C) ${optC}</span>
          <span>(D) ${optD}</span>
          <span style="color:var(--teal-700);font-weight:600;">[答案: ${p.answer || '?'}]</span>
        </div>
      </td>
      <td><span class="tag" style="background:var(--blue-50);color:var(--blue-800);">${p.subject || '一般'} / ${p.unit || '未分類'}</span></td>
      <td>
        <span class="tag ${isPpt ? 'doc' : isPdf ? 'pdf' : 'xls'}">
          <i class="ti ${isPpt ? 'ti-file-type-ppt' : isPdf ? 'ti-file-type-pdf' : 'ti-file-type-xls'}"></i> ${source}
        </span>
      </td>
      <td><span class="confidence-badge ${confBadgeClass}"><i class="ti ti-shield-check"></i> ${conf}%</span></td>
      <td><span class="status-badge pending">待確認</span></td>
      <td class="row-actions">
        <button class="ghost" style="padding:4px 8px;font-size:12px;color:var(--teal-700);" onclick="confirmPending(${p.id})"><i class="ti ti-check"></i>確認</button>
        <i class="ti ti-edit" title="編輯" onclick="editPending(${p.id})"></i>
        <i class="ti ti-trash" title="刪除" onclick="deletePending(${p.id})"></i>
      </td>
    </tr>`;
  }).join('');
}

async function confirmPending(id) {
  await ImportAPI.confirm(id);
  renderPendingTable();
  toast('題目已確認並順利登錄至題庫！', 'success');
}

async function confirmAllPendingToBank() {
  const list = DB.pending.filter(p => p.status === 'pending');
  if (!list.length) { toast('目前沒有待確認的題目', 'warning'); return; }
  const count = await ImportAPI.confirmAll();
  renderPendingTable();
  toast(`一鍵確認成功！已將 ${count} 筆題目登錄至題庫管理中`, 'success');
}

function clearAllPendingRecords() {
  const list = DB.pending.filter(p => p.status === 'pending');
  if (!list.length) { toast('目前沒有待確認的題目', 'warning'); return; }
  Modal.open({
    title: '清空待確認清單',
    body: `<div class="confirm-icon">🗑️</div><div class="confirm-text">確定要清空所有 ${list.length} 筆待確認題目嗎？<br>此動作無法復原。</div>`,
    confirmText: '清空全部',
    confirmClass: 'danger',
    async onConfirm() {
      if (USE_API) {
        for (const item of list) {
          try { await ImportAPI.remove(item.id); } catch (e) { }
        }
      }
      DB.pending = DB.pending.filter(p => p.status !== 'pending');
      Modal.close();
      renderPendingTable();
      syncDashboard();
      toast('待確認清單已成功清空', 'warning');
    }
  });
}

function editPending(id) {
  const p = DB.pending.find(p => p.id === id);
  if (!p) return;

  Modal.open({
    title: '編輯待確認題目',
    body: `
      <div class="form-group"><label>題目內容</label><textarea id="ep-content" rows="3">${p.content}</textarea></div>
      <div class="form-row">
        <div class="form-group"><label>選項 A</label><input type="text" id="ep-a" value="${p.optA}"></div>
        <div class="form-group"><label>選項 B</label><input type="text" id="ep-b" value="${p.optB}"></div>
      </div>
      <div class="form-row">
        <div class="form-group"><label>選項 C</label><input type="text" id="ep-c" value="${p.optC}"></div>
        <div class="form-group"><label>選項 D</label><input type="text" id="ep-d" value="${p.optD}"></div>
      </div>
      <div class="form-row">
        <div class="form-group"><label>正確答案</label>
          <select id="ep-ans">
            <option ${p.answer === 'A' ? 'selected' : ''}>A</option>
            <option ${p.answer === 'B' ? 'selected' : ''}>B</option>
            <option ${p.answer === 'C' ? 'selected' : ''}>C</option>
            <option ${p.answer === 'D' ? 'selected' : ''}>D</option>
          </select>
        </div>
        <div class="form-group"><label>科目</label><input type="text" id="ep-subj" value="${p.subject || ''}"></div>
      </div>
      <div class="form-group"><label>單元</label><input type="text" id="ep-unit" value="${p.unit || ''}"></div>`,
    confirmText: '儲存並確認',
    async onConfirm() {
      const data = {
        content: document.getElementById('ep-content').value.trim(),
        optA: document.getElementById('ep-a').value.trim(),
        optB: document.getElementById('ep-b').value.trim(),
        optC: document.getElementById('ep-c').value.trim(),
        optD: document.getElementById('ep-d').value.trim(),
        answer: document.getElementById('ep-ans').value,
        subject: document.getElementById('ep-subj').value.trim(),
        unit: document.getElementById('ep-unit').value.trim(),
        confidence: p.confidence
      };
      if (!data.content) { toast('題目內容不可空白', 'error'); return; }
      await ImportAPI.update(id, data);
      await ImportAPI.confirm(id);
      Modal.close();
      renderPendingTable();
      toast('題目已修改並加入題庫', 'success');
    }
  });
}

function deletePending(id) {
  Modal.open({
    title: '確認刪除',
    body: `<div class="confirm-icon">🗑️</div><div class="confirm-text">確定要刪除此筆待確認題目嗎？<br>此動作無法復原。</div>`,
    confirmText: '刪除',
    confirmClass: 'danger',
    async onConfirm() {
      await ImportAPI.remove(id);
      Modal.close();
      renderPendingTable();
      toast('待確認題目已刪除', 'warning');
    }
  });
}

/* ============================================================
   SECTION 7: BANK PANEL
   ============================================================ */
let bankFilters = { dept: '', subject: '', unit: '', difficulty: '', keyword: '' };
let bankPage = 1;
const PAGE_SIZE = 8;
let expandedQId = null;

async function initBankPanel() {
  if (USE_API) {
    const all = await QuestionAPI.list({});
    DB.questions = all;
  }
  buildBankFilterOptions();
  renderBankPanel();

  document.getElementById('bankSearch').addEventListener('input', debounce(() => {

    bankFilters.keyword = document.getElementById('bankSearch').value.trim();
    bankPage = 1; renderBankPanel();
  }, 300));

  ['bankDept', 'bankSubject', 'bankUnit', 'bankDifficulty'].forEach(id => {
    document.getElementById(id)?.addEventListener('change', () => {
      bankFilters.dept = document.getElementById('bankDept').value;
      bankFilters.subject = document.getElementById('bankSubject').value;
      bankFilters.unit = document.getElementById('bankUnit').value;
      bankFilters.difficulty = document.getElementById('bankDifficulty').value;
      bankPage = 1; renderBankPanel();
    });
  });

  document.getElementById('bankAddBtn').addEventListener('click', openAddQuestionModal);
}

function buildBankFilterOptions() {
  const depts = [...new Set(DB.questions.map(q => q.dept))];
  const subjects = [...new Set(DB.questions.map(q => q.subject))];
  const units = [...new Set(DB.questions.map(q => q.unit))];
  const diffs = ['易', '中', '難'];
  fillSelect('bankDept', depts, '全部科系');
  fillSelect('bankSubject', subjects, '全部科目');
  fillSelect('bankUnit', units, '全部單元');
  fillSelect('bankDifficulty', diffs, '全部難度');
}

function fillSelect(id, options, placeholder) {
  const el = document.getElementById(id);
  if (!el) return;
  el.innerHTML = `<option value="">${placeholder}</option>` + options.map(o => `<option>${o}</option>`).join('');
}

async function renderBankPanel() {
  const list = await QuestionAPI.list(bankFilters);
  const total = list.length;
  const totalPages = Math.ceil(total / PAGE_SIZE) || 1;
  if (bankPage > totalPages) bankPage = totalPages;
  const paged = list.slice((bankPage - 1) * PAGE_SIZE, bankPage * PAGE_SIZE);

  document.getElementById('bankTotalCount').textContent = `共 ${total} 筆題目`;
  const tbody = document.getElementById('bankTbody');

  if (!paged.length) {
    tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;color:var(--ink-mute);padding:28px;">查無符合條件的題目</td></tr>`;
    document.getElementById('bankPagination').innerHTML = '';
    return;
  }

  tbody.innerHTML = paged.map(q => {
    const src = q.source === 'PDF匯入' ? 'pdf' : q.source === 'AI產生' || q.source === 'AI統整' ? 'ai' : 'manual';
    const expanded = expandedQId === q.id;
    return `
      <tr class="q-main-row" style="cursor:pointer" onclick="toggleExpand(${q.id})">
        <td><i class="ti ${expanded ? 'ti-chevron-down' : 'ti-chevron-right'}" style="font-size:13px;color:var(--ink-mute);margin-right:6px;"></i>${q.content.length > 50 ? q.content.slice(0, 50) + '…' : q.content}</td>
        <td>${q.subject}</td>
        <td>${q.unit}</td>
        <td><span class="diff-badge ${q.difficulty}">${q.difficulty}</span></td>
        <td><span class="tag ${src}">${q.source}</span></td>
        <td class="row-actions">
          <i class="ti ti-edit" title="編輯" onclick="event.stopPropagation();editQuestion(${q.id})"></i>
          <i class="ti ti-trash" title="刪除" onclick="event.stopPropagation();deleteQuestion(${q.id})"></i>
        </td>
      </tr>
      ${expanded ? `<tr class="q-expand-row"><td colspan="6"><div class="q-expand-content">
        <div style="font-size:13px;font-weight:500;margin-bottom:8px;color:var(--ink);">${q.content}</div>
        <div class="q-opts">
          <div class="q-opt ${q.answer === 'A' ? 'correct' : ''}"><b>A.</b> ${q.optA} ${q.answer === 'A' ? '✓' : ''}</div>
          <div class="q-opt ${q.answer === 'B' ? 'correct' : ''}"><b>B.</b> ${q.optB} ${q.answer === 'B' ? '✓' : ''}</div>
          <div class="q-opt ${q.answer === 'C' ? 'correct' : ''}"><b>C.</b> ${q.optC} ${q.answer === 'C' ? '✓' : ''}</div>
          <div class="q-opt ${q.answer === 'D' ? 'correct' : ''}"><b>D.</b> ${q.optD} ${q.answer === 'D' ? '✓' : ''}</div>
        </div>
      </div></td></tr>`: ''}
    `;
  }).join('');

  // Pagination
  let pgHtml = `<span class="page-info">第 ${(bankPage - 1) * PAGE_SIZE + 1}–${Math.min(bankPage * PAGE_SIZE, total)} 筆 / 共 ${total} 筆</span>`;
  pgHtml += `<button class="page-btn" ${bankPage <= 1 ? 'disabled' : ''} onclick="bankGoPage(${bankPage - 1})"><i class="ti ti-chevron-left"></i></button>`;
  for (let i = 1; i <= totalPages; i++) {
    pgHtml += `<button class="page-btn ${i === bankPage ? 'active' : ''}" onclick="bankGoPage(${i})">${i}</button>`;
  }
  pgHtml += `<button class="page-btn" ${bankPage >= totalPages ? 'disabled' : ''} onclick="bankGoPage(${bankPage + 1})"><i class="ti ti-chevron-right"></i></button>`;
  document.getElementById('bankPagination').innerHTML = pgHtml;
}

function toggleExpand(id) {
  expandedQId = expandedQId === id ? null : id;
  renderBankPanel();
}

function bankGoPage(p) { bankPage = p; expandedQId = null; renderBankPanel(); }

function questionModalForm(q = null) {
  return `
    <div class="form-group"><label>題目內容 *</label><textarea id="qm-content" rows="3" placeholder="請輸入題目">${q ? q.content : ''}</textarea></div>
    <div class="form-row">
      <div class="form-group"><label>選項 A *</label><input type="text" id="qm-a" value="${q ? q.optA : ''}" placeholder="選項A"></div>
      <div class="form-group"><label>選項 B *</label><input type="text" id="qm-b" value="${q ? q.optB : ''}" placeholder="選項B"></div>
    </div>
    <div class="form-row">
      <div class="form-group"><label>選項 C *</label><input type="text" id="qm-c" value="${q ? q.optC : ''}" placeholder="選項C"></div>
      <div class="form-group"><label>選項 D *</label><input type="text" id="qm-d" value="${q ? q.optD : ''}" placeholder="選項D"></div>
    </div>
    <div class="form-row">
      <div class="form-group"><label>正確答案</label>
        <select id="qm-ans">
          <option ${!q || q.answer === 'A' ? 'selected' : ''}>A</option>
          <option ${q?.answer === 'B' ? 'selected' : ''}>B</option>
          <option ${q?.answer === 'C' ? 'selected' : ''}>C</option>
          <option ${q?.answer === 'D' ? 'selected' : ''}>D</option>
        </select>
      </div>
      <div class="form-group"><label>難度</label>
        <select id="qm-diff">
          <option ${!q || q.difficulty === '易' ? 'selected' : ''}>易</option>
          <option ${q?.difficulty === '中' ? 'selected' : ''}>中</option>
          <option ${q?.difficulty === '難' ? 'selected' : ''}>難</option>
        </select>
      </div>
    </div>
    <div class="form-row">
      <div class="form-group"><label>科目</label><input type="text" id="qm-subj" value="${q ? q.subject : ''}" placeholder="例：物理"></div>
      <div class="form-group"><label>單元</label><input type="text" id="qm-unit" value="${q ? q.unit : ''}" placeholder="例：力學"></div>
    </div>`;
}

function getQFormData() {
  return {
    content: document.getElementById('qm-content').value.trim(),
    optA: document.getElementById('qm-a').value.trim(),
    optB: document.getElementById('qm-b').value.trim(),
    optC: document.getElementById('qm-c').value.trim(),
    optD: document.getElementById('qm-d').value.trim(),
    answer: document.getElementById('qm-ans').value,
    difficulty: document.getElementById('qm-diff').value,
    subject: document.getElementById('qm-subj').value.trim() || '未分類',
    unit: document.getElementById('qm-unit').value.trim() || '未分類',
    dept: '自然科學科',
    source: '教師手動'
  };
}

function openAddQuestionModal() {
  Modal.open({
    title: '新增題目',
    body: questionModalForm(),
    confirmText: '新增',
    async onConfirm() {
      const data = getQFormData();
      if (!data.content || !data.optA || !data.optB || !data.optC || !data.optD) {
        toast('請填寫必填欄位（題目與四個選項）', 'error'); return;
      }
      await QuestionAPI.create(data);
      Modal.close();
      buildBankFilterOptions();
      renderBankPanel();
      toast('題目新增成功', 'success');
    }
  });
}

function editQuestion(id) {
  const q = DB.questions.find(q => q.id === id);
  if (!q) return;
  Modal.open({
    title: '編輯題目',
    body: questionModalForm(q),
    confirmText: '儲存',
    async onConfirm() {
      const data = getQFormData();
      if (!data.content) { toast('題目內容不可空白', 'error'); return; }
      await QuestionAPI.update(id, data);
      Modal.close();
      renderBankPanel();
      toast('題目已更新', 'success');
    }
  });
}

function deleteQuestion(id) {
  const q = DB.questions.find(q => q.id === id);
  Modal.open({
    title: '確認刪除',
    body: `<div class="confirm-icon">🗑️</div><div class="confirm-text">確定要刪除以下題目嗎？<br><br><strong style="color:var(--ink)">${q?.content.slice(0, 60)}…</strong><br><br>此動作無法復原。</div>`,
    confirmText: '刪除',
    confirmClass: 'danger',
    async onConfirm() {
      await QuestionAPI.remove(id);
      Modal.close();
      expandedQId = null;
      buildBankFilterOptions();
      renderBankPanel();
      toast('題目已刪除', 'warning');
    }
  });
}

function debounce(fn, delay) {
  let t; return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), delay); };
}

/* ============================================================
   SECTION 8: FOLDER PANEL
   ============================================================ */
let currentFolderId = null;

function initFolderPanel() {
  renderFolderList();
  document.getElementById('createFolderBtn').addEventListener('click', openCreateFolderModal);
}

async function renderFolderList() {
  const el = document.getElementById('folderListView');
  const folders = await FolderAPI.list();
  DB.folders = folders;
  document.getElementById('folderDetailView').style.display = 'none';
  el.style.display = 'block';

  const listEl = document.getElementById('folderList');
  if (!folders.length) {
    listEl.innerHTML = `<div class="placeholder-card"><i class="ti ti-folder-off"></i>尚無學習資料夾，點擊「建立資料夾」開始</div>`;
    return;
  }
  listEl.innerHTML = folders.map(f => `
    <div class="fk-item">
      <div class="fk-left">
        <div class="fk-icon"><i class="ti ti-folder"></i></div>
        <div>
          <div class="fk-title">${f.name}</div>
          <div class="fk-sub">${f.materials.length} 份教材（${f.materials.map(m => m.type).filter((v, i, a) => a.indexOf(v) === i).join('、') || '—'}）
            ${f.generatedCount > 0 ? `· 已生成 ${f.generatedCount} 題統整題` : ''}
          </div>
        </div>
      </div>
      <div class="fk-actions">
        ${f.generatedCount > 0 ? `<span class="fk-badge"><i class="ti ti-sparkles"></i> ${f.generatedCount} 題</span>` : ''}
        <button class="ghost" onclick="openFolderDetail(${f.id})"><i class="ti ti-folder-open"></i>開啟</button>
        <i class="ti ti-trash" style="font-size:15px;color:var(--ink-mute);cursor:pointer;" onclick="deleteFolder(${f.id})" title="刪除資料夾"></i>
      </div>
    </div>
  `).join('');
}

function openCreateFolderModal() {
  Modal.open({
    title: '建立學習資料夾',
    body: `<div class="form-group"><label>資料夾名稱 *</label><input type="text" id="fld-name" placeholder="例：高三物理 — 期末總複習"></div>`,
    confirmText: '建立',
    async onConfirm() {
      const name = document.getElementById('fld-name').value.trim();
      if (!name) { toast('請輸入資料夾名稱', 'error'); return; }
      await FolderAPI.create(name);
      Modal.close();
      await renderFolderList();
      toast('資料夾已建立', 'success');
    }
  });
}

function deleteFolder(id) {
  const f = DB.folders.find(f => f.id === id);
  Modal.open({
    title: '確認刪除資料夾',
    body: `<div class="confirm-icon">📁</div><div class="confirm-text">確定要刪除「<strong>${f?.name}</strong>」及其所有教材？<br>此動作無法復原。</div>`,
    confirmText: '刪除',
    confirmClass: 'danger',
    async onConfirm() {
      await FolderAPI.remove(id);
      Modal.close();
      await renderFolderList();
      toast('資料夾已刪除', 'warning');
    }
  });
}

function openFolderDetail(id) {
  currentFolderId = id;
  const folder = DB.folders.find(f => f.id === id);
  if (!folder) return;

  document.getElementById('folderListView').style.display = 'none';
  const detail = document.getElementById('folderDetailView');
  detail.style.display = 'block';

  document.getElementById('folderDetailBreadcrumb').innerHTML =
    `<a onclick="renderFolderList()"><i class="ti ti-folder"></i> 學習資料夾</a>
     <i class="ti ti-chevron-right"></i>
     <span>${folder.name}</span>`;

  renderFolderDetailContent(folder);
}

function renderFolderDetailContent(folder) {
  // Materials
  const matList = document.getElementById('folderMaterialList');
  const matTypeMap = { PDF: 'ti-file-type-pdf', PPT: 'ti-presentation', DOC: 'ti-file-type-doc', Excel: 'ti-file-type-xls', 圖片: 'ti-photo' };
  const matColorMap = { PDF: '#1565c0', PPT: '#e65100', DOC: '#283593', Excel: '#2e7d32', 圖片: '#6a1b9a' };
  matList.innerHTML = folder.materials.length
    ? folder.materials.map(m => `
      <div class="material-item">
        <i class="ti ${matTypeMap[m.type] || 'ti-file'} mi-icon" style="color:${matColorMap[m.type] || 'var(--ink-mute)'}"></i>
        <span class="mi-name">${m.name}</span>
        <span class="mi-type tag ${m.type === 'PDF' ? 'pdf' : m.type === 'PPT' ? 'ppt' : m.type === 'Excel' ? 'xls' : 'doc'}">${m.type}</span>
        <i class="ti ti-x mi-remove" onclick="removeMaterial(${folder.id},${m.id})" title="移除教材"></i>
      </div>`).join('')

    : `<div style="color:var(--ink-mute);font-size:13px;padding:12px 0;">尚未上傳任何教材</div>`;

  // Generated questions
  renderGeneratedQuestions(folder);
}

async function removeMaterial(folderId, matId) {
  await FolderAPI.removeMaterial(folderId, matId);
  await renderFolderList();
  const folder = DB.folders.find(f => f.id === folderId);
  if (folder) renderFolderDetailContent(folder);
  toast('教材已移除', 'warning');
}

function initFolderUpload() {
  const zone = document.getElementById('folderUploadZone');
  const input = document.getElementById('folderFileInput');
  if (!zone) return;
  zone.addEventListener('click', () => input.click());
  zone.addEventListener('dragover', e => { e.preventDefault(); zone.classList.add('dragover'); });
  zone.addEventListener('dragleave', () => zone.classList.remove('dragover'));
  zone.addEventListener('drop', e => {
    e.preventDefault(); zone.classList.remove('dragover');
    [...e.dataTransfer.files].forEach(f => handleFolderFile(f));
  });
  input.addEventListener('change', () => {
    [...input.files].forEach(f => handleFolderFile(f));
    input.value = '';
  });
}

async function handleFolderFile(file) {
  if (!currentFolderId) return;
  const ext = file.name.split('.').pop().toLowerCase();
  let type = 'PDF';
  if (['pptx', 'ppt'].includes(ext)) type = 'PPT';
  else if (['docx', 'doc'].includes(ext)) type = 'DOC';
  else if (['xlsx', 'xls'].includes(ext)) type = 'Excel';
  else if (['png', 'jpg', 'jpeg'].includes(ext)) type = '圖片';
  await FolderAPI.addMaterial(currentFolderId, file, file.name, type);
  await renderFolderList();
  const folder = DB.folders.find(f => f.id === currentFolderId);
  if (folder) renderFolderDetailContent(folder);
  toast(`已上傳「${file.name}」`, 'success');
}

async function generateFolderQuestions() {
  const folder = DB.folders.find(f => f.id === currentFolderId);
  if (!folder) return;
  if (folder.materials.length < 2) { toast('請至少上傳 2 份教材以生成統整題', 'warning'); return; }

  const btn = document.getElementById('generateBtn');
  btn.disabled = true;
  const loading = document.getElementById('genLoading');
  const result = document.getElementById('genResult');
  loading.style.display = 'block';
  result.innerHTML = '';

  // Animate AI steps
  const steps = [
    { id: 'aiStep1', text: '分析教材內容結構…' },
    { id: 'aiStep2', text: '提取各章節核心概念…' },
    { id: 'aiStep3', text: '交叉比對跨章節知識點…' },
    { id: 'aiStep4', text: '生成統整題目…' },
  ];
  steps.forEach((s, i) => {
    const el = document.getElementById(s.id);
    if (el) el.className = 'ai-step-item';
    setTimeout(() => {
      if (el) {
        el.innerHTML = `<i class="ti ti-check-circle"></i><span>${s.text}</span>`;
        el.className = 'ai-step-item done';
      }
    }, (i + 1) * 800);
  });

  try {
    const qs = await FolderAPI.generate(currentFolderId);
    loading.style.display = 'none';
    btn.disabled = false;
    renderGeneratedQuestions(folder);
    toast(`AI 已生成 ${qs.length} 道跨章節統整題！`, 'success');
  } catch (e) {
    loading.style.display = 'none';
    btn.disabled = false;
    toast('生成失敗，請稍後再試', 'error');
  }
}

function renderGeneratedQuestions(folder) {
  const result = document.getElementById('genResult');
  if (!result) return;
  const genBtn = document.getElementById('generateBtn');

  if (!folder.generated || !folder.generated.length) {
    result.innerHTML = '';
    if (genBtn) genBtn.style.display = '';
    return;
  }
  if (genBtn) genBtn.style.display = 'none';

  result.innerHTML = `
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;">
      <div class="section-title" style="margin:0;">AI 生成統整題（${folder.generated.length} 道）</div>
      <button class="ghost" onclick="regenerate()" style="font-size:12px;"><i class="ti ti-refresh"></i>重新生成</button>
    </div>
    ${folder.generated.map(q => `
    <div class="gen-q-card" id="gqcard-${q.gid}">
      <div class="gen-q-header">
        <span class="gen-q-badge"><i class="ti ti-sparkles"></i> ${q.chapter}</span>
        <div class="gen-q-text">${q.content}</div>
      </div>
      <div class="gen-q-opts">
        <div class="gen-q-opt ${q.answer === 'A' ? 'correct' : ''}"><b>A.</b> ${q.optA} ${q.answer === 'A' ? '✓' : ''}</div>
        <div class="gen-q-opt ${q.answer === 'B' ? 'correct' : ''}"><b>B.</b> ${q.optB} ${q.answer === 'B' ? '✓' : ''}</div>
        <div class="gen-q-opt ${q.answer === 'C' ? 'correct' : ''}"><b>C.</b> ${q.optC} ${q.answer === 'C' ? '✓' : ''}</div>
        <div class="gen-q-opt ${q.answer === 'D' ? 'correct' : ''}"><b>D.</b> ${q.optD} ${q.answer === 'D' ? '✓' : ''}</div>
      </div>
      <div class="gen-q-footer">
        ${q.added
      ? `<span class="added-note"><i class="ti ti-check-circle"></i>已加入題庫</span>`
      : `<button class="primary" style="font-size:12px;" onclick="importGenQ('${q.gid}')"><i class="ti ti-plus"></i>加入題庫</button>
             <button class="ghost" style="font-size:12px;" onclick="editGenQ('${q.gid}')"><i class="ti ti-edit"></i>修改後加入</button>`
    }
      </div>
    </div>`).join('')}`;
}

function regenerate() {
  const folder = DB.folders.find(f => f.id === currentFolderId);
  if (!folder) return;
  folder.generated = [];
  folder.generatedCount = 0;
  const genBtn = document.getElementById('generateBtn');
  if (genBtn) genBtn.style.display = '';
  document.getElementById('genResult').innerHTML = '';
  // Reset AI steps
  ['aiStep1', 'aiStep2', 'aiStep3', 'aiStep4'].forEach(id => {
    const el = document.getElementById(id);
    if (el) { el.className = 'ai-step-item'; el.innerHTML = `<i class="ti ti-circle-dashed"></i><span>${el.querySelector('span')?.textContent || ''}</span>`; }
  });
  toast('已重置，請重新生成', 'warning');
}

async function importGenQ(gid) {
  const folder = DB.folders.find(f => f.id === currentFolderId);
  const q = folder?.generated.find(q => q.gid === gid);
  if (!q) return;
  await FolderAPI.importGenerated(currentFolderId, q);
  q.added = true;
  renderGeneratedQuestions(folder);
  toast('統整題已加入題庫', 'success');
}

function editGenQ(gid) {
  const folder = DB.folders.find(f => f.id === currentFolderId);
  const q = folder?.generated.find(q => q.gid === gid);
  if (!q) return;
  Modal.open({
    title: '修改統整題後加入題庫',
    body: `
      <div class="form-group"><label>題目內容</label><textarea id="gq-content" rows="4">${q.content}</textarea></div>
      <div class="form-row">
        <div class="form-group"><label>選項 A</label><input type="text" id="gq-a" value="${q.optA}"></div>
        <div class="form-group"><label>選項 B</label><input type="text" id="gq-b" value="${q.optB}"></div>
      </div>
      <div class="form-row">
        <div class="form-group"><label>選項 C</label><input type="text" id="gq-c" value="${q.optC}"></div>
        <div class="form-group"><label>選項 D</label><input type="text" id="gq-d" value="${q.optD}"></div>
      </div>
      <div class="form-row">
        <div class="form-group"><label>正確答案</label>
          <select id="gq-ans">
            <option ${q.answer === 'A' ? 'selected' : ''}>A</option>
            <option ${q.answer === 'B' ? 'selected' : ''}>B</option>
            <option ${q.answer === 'C' ? 'selected' : ''}>C</option>
            <option ${q.answer === 'D' ? 'selected' : ''}>D</option>
          </select>
        </div>
        <div class="form-group"><label>難度</label>
          <select id="gq-diff"><option>難</option><option>中</option><option>易</option></select>
        </div>
      </div>`,
    confirmText: '修改並加入題庫',
    async onConfirm() {
      const data = {
        content: document.getElementById('gq-content').value.trim(),
        optA: document.getElementById('gq-a').value.trim(),
        optB: document.getElementById('gq-b').value.trim(),
        optC: document.getElementById('gq-c').value.trim(),
        optD: document.getElementById('gq-d').value.trim(),
        answer: document.getElementById('gq-ans').value,
        difficulty: document.getElementById('gq-diff').value,
        subject: q.subject, unit: q.chapter, dept: '自然科學科', source: 'AI統整'
      };
      Object.assign(q, data);
      await FolderAPI.importGenerated(currentFolderId, q);
      q.added = true;
      Modal.close();
      renderGeneratedQuestions(folder);
      toast('修改後的統整題已加入題庫', 'success');
    }
  });
}

/* ============================================================
   SECTION 9: ROLE & NAV CONFIG
   ============================================================ */
const roles = {
  teacher: {
    name: "王老師", avatar: "王",
    nav: [
      { id: "t-dash", icon: "ti-layout-dashboard", label: "儀表板" },
      { id: "t-import", icon: "ti-upload", label: "題庫匯入" },
      { id: "t-bank", icon: "ti-folder", label: "題庫管理" },
      { id: "t-folder", icon: "ti-files", label: "學習資料夾" },
      { id: "t-paper", icon: "ti-clipboard-list", label: "固定試卷" },
      { id: "t-analysis", icon: "ti-chart-bar", label: "成效分析" },
      { id: "t-perm", icon: "ti-users", label: "小老師權限" },
      { id: "t-announce", icon: "ti-speakerphone", label: "課程公告" },
      { id: "t-course", icon: "ti-school", label: "課程管理" },
    ], default: "t-dash"
  },
  assistant: {
    name: "李小老師", avatar: "李",
    nav: [
      { id: "a-dash", icon: "ti-layout-dashboard", label: "儀表板" },
      { id: "a-import", icon: "ti-upload", label: "題庫匯入", locked: true },
      { id: "a-bank", icon: "ti-folder", label: "題庫管理", locked: true },
      { id: "a-folder", icon: "ti-files", label: "跨文件統整測驗", locked: true },
      { id: "a-announce", icon: "ti-speakerphone", label: "課程公告" },
    ], default: "a-dash"
  },
  student: {
    name: "陳同學", avatar: "陳",
    nav: [
      { id: "s-home", icon: "ti-home", label: "首頁" },
      { id: "s-quiz", icon: "ti-pencil", label: "開始測驗" },
      { id: "s-weak", icon: "ti-target-arrow", label: "弱點分析" },
      { id: "s-wrong", icon: "ti-repeat", label: "錯題本" },
      { id: "s-remind", icon: "ti-clock", label: "複習提醒" },
      { id: "s-analysis", icon: "ti-chart-line", label: "學習成效分析" },
      { id: "s-bankinfo", icon: "ti-database", label: "題庫資訊" },
      { id: "s-record", icon: "ti-history", label: "自我評量紀錄" },
      { id: "s-announce", icon: "ti-speakerphone", label: "課程公告" },
    ], default: "s-home"
  },
  admin: {
    name: "系統管理員", avatar: "管",
    nav: [
      { id: "m-dash", icon: "ti-layout-dashboard", label: "儀表板" },
      { id: "m-account", icon: "ti-user-cog", label: "帳號管理" },
      { id: "m-perm", icon: "ti-shield-lock", label: "權限設定" },
      { id: "m-announce", icon: "ti-speakerphone", label: "系統公告" },
      { id: "m-usage", icon: "ti-chart-donut", label: "系統用量" },
      { id: "m-log", icon: "ti-alert-triangle", label: "錯誤紀錄" },
      { id: "m-course", icon: "ti-school", label: "課程資料管理" },
    ], default: "m-dash"
  }
};

const roleLabel = { teacher: "老師", assistant: "小老師", student: "學生", admin: "管理者" };

function renderSidebar(roleKey) {
  const role = roles[roleKey];
  document.getElementById('sidebar').innerHTML =
    '<div class="side-section-label">功能選單</div>' +
    role.nav.map(item => `
      <div class="nav-item" data-panel="${item.id}">
        <i class="ti ${item.icon}"></i>
        <span>${item.label}</span>
        ${item.locked ? '<span class="nav-note">需授權</span>' : ''}
      </div>`).join('');
  document.querySelectorAll('.nav-item').forEach(el => {
    el.addEventListener('click', () => showPanel(el.dataset.panel));
  });
}

let panelInited = { import: false, bank: false, folder: false, course: false };

function showPanel(panelId) {
  document.querySelectorAll('.nav-item').forEach(el => el.classList.toggle('active', el.dataset.panel === panelId));
  document.querySelectorAll('.panel').forEach(el => el.classList.toggle('active', el.id === panelId));
  if (panelId === 't-import' && !panelInited.import) { initImportPanel(); panelInited.import = true; }
  if (panelId === 't-bank' && !panelInited.bank) { initBankPanel(); panelInited.bank = true; }
  if (panelId === 't-folder' && !panelInited.folder) { initFolderPanel(); panelInited.folder = true; }
  if (panelId === 't-course' || panelId === 'm-course') { renderCourseList(); }
  if (panelId === 't-perm') { renderTeacherPermPanel(); }
}

/* ============================================================
   SECTION 8B: 課程管理與 Excel 學生名單匯入 (Course Management & Excel Roster)
   ============================================================ */
let currentCourseStudents = [];
let currentEditingCourseId = null;
let lastActiveCoursePanel = 't-course';

function getCurrentTeacherName() {
  const userEl = document.getElementById('userName');
  if (userEl && userEl.textContent.trim()) {
    return userEl.textContent.trim();
  }
  if (typeof loginRole !== 'undefined' && roles && roles[loginRole]) {
    return roles[loginRole].name;
  }
  return '王大明';
}

function openCreateCoursePage() {
  const activePanel = document.querySelector('.panel.active');
  if (activePanel && (activePanel.id === 't-course' || activePanel.id === 'm-course')) {
    lastActiveCoursePanel = activePanel.id;
  }
  currentEditingCourseId = null;

  // Update UI texts for creation
  const setTitle = (id, text) => { const el = document.getElementById(id); if (el) el.textContent = text; };
  setTitle('courseFormTitle', '新增課程');
  setTitle('courseFormSub', '請填寫課程名稱與開課班級，並透過 Excel / CSV 批次匯入修課學生名單');
  setTitle('courseFormBreadcrumb', '新增課程');
  setTitle('courseFormTopBtnText', '儲存並建立課程');
  setTitle('courseFormBottomBtnText', '儲存並建立課程');

  // Reset form
  resetCreateCourseForm();

  // Show create course panel
  document.querySelectorAll('.panel').forEach(el => el.classList.remove('active'));
  const createPanel = document.getElementById('t-course-create');
  if (createPanel) createPanel.classList.add('active');
  initStudentDropZone();
}

async function openEditCoursePage(courseId) {
  const activePanel = document.querySelector('.panel.active');
  if (activePanel && (activePanel.id === 't-course' || activePanel.id === 'm-course')) {
    lastActiveCoursePanel = activePanel.id;
  }

  const course = (DB.courses || []).find(c => c.id === courseId);
  if (!course) {
    toast("找不到該課程資料", "error");
    return;
  }

  currentEditingCourseId = courseId;

  // Update UI texts for editing
  const setTitle = (id, text) => { const el = document.getElementById(id); if (el) el.textContent = text; };
  setTitle('courseFormTitle', `編輯課程 — ${course.name}`);
  setTitle('courseFormSub', `修改課程基本資訊與維護修課學生名單（${course.grade || '高二'} ${course.classGroup || '忠班'}）`);
  setTitle('courseFormBreadcrumb', '編輯課程');
  setTitle('courseFormTopBtnText', '儲存變更');
  setTitle('courseFormBottomBtnText', '儲存變更');

  // Populate fields
  const setVal = (id, val) => { const el = document.getElementById(id); if (el) el.value = val || ''; };
  setVal('courseName', course.name);
  setVal('courseGrade', course.grade || '高二');
  setVal('courseClass', course.classGroup || '忠班');
  setVal('courseTeacher', course.teacher || getCurrentTeacherName());

  const rawStudents = course.students ? JSON.parse(JSON.stringify(course.students)) : [];
  if (rawStudents.length > 0) {
    const verifyRes = await UserAPI.verifyStudents(rawStudents);
    currentCourseStudents = (verifyRes.students || []).map((st, i) => {
      const orig = rawStudents[i] || {};
      return {
        ...st,
        isAssistant: orig.isAssistant || false,
        assistantPermissions: orig.assistantPermissions || ''
      };
    });
  } else {
    currentCourseStudents = [];
  }
  renderStudentPreview();

  const fileInfo = document.getElementById('studentFileLoadedInfo');
  if (fileInfo) {
    if (currentCourseStudents.length > 0) {
      fileInfo.style.display = 'flex';
      document.getElementById('studentFileName').textContent = `${course.name} 現有名單`;
      document.getElementById('studentFileSize').textContent = `(共 ${currentCourseStudents.length} 筆學生資料)`;
    } else {
      fileInfo.style.display = 'none';
    }
  }

  // Show create/edit course panel
  document.querySelectorAll('.panel').forEach(el => el.classList.remove('active'));
  const createPanel = document.getElementById('t-course-create');
  if (createPanel) createPanel.classList.add('active');
  initStudentDropZone();
}

function closeCreateCoursePage() {
  document.querySelectorAll('.panel').forEach(el => el.classList.remove('active'));
  const target = document.getElementById(lastActiveCoursePanel) || document.getElementById('t-course');
  if (target) target.classList.add('active');
  renderCourseList();
}

function resetCreateCourseForm() {
  const form = document.getElementById('createCourseForm');
  if (form) form.reset();

  // Reset default values
  const setVal = (id, val) => { const el = document.getElementById(id); if (el) el.value = val; };
  setVal('courseGrade', '高二');
  setVal('courseClass', '忠班');
  setVal('courseTeacher', getCurrentTeacherName());

  currentCourseStudents = [];
  renderStudentPreview();

  const fileInfo = document.getElementById('studentFileLoadedInfo');
  if (fileInfo) fileInfo.style.display = 'none';
  const fileInput = document.getElementById('studentExcelInput');
  if (fileInput) fileInput.value = '';
}

function downloadStudentExcelTemplate() {
  const sampleData = [
    { "學號": "S1130101", "姓名": "張廷瑋", "座號": "01", "電子信箱": "s1130101@school.edu.tw", "備註": "班長" },
    { "學號": "S1130102", "姓名": "林語晨", "座號": "02", "電子信箱": "s1130102@school.edu.tw", "備註": "副班長" },
    { "學號": "S1130103", "姓名": "陳冠宇", "座號": "03", "電子信箱": "s1130103@school.edu.tw", "備註": "" },
    { "學號": "S1130104", "姓名": "黃品瑄", "座號": "04", "電子信箱": "s1130104@school.edu.tw", "備註": "" },
    { "學號": "S1130105", "姓名": "趙韋翔", "座號": "05", "電子信箱": "s1130105@school.edu.tw", "備註": "" }
  ];

  if (typeof XLSX !== 'undefined') {
    const ws = XLSX.utils.json_to_sheet(sampleData);
    // Set column widths
    ws['!cols'] = [{ wch: 14 }, { wch: 12 }, { wch: 8 }, { wch: 28 }, { wch: 16 }];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "學生名單");
    XLSX.writeFile(wb, "課程修課學生名單範本.xlsx");
    toast("已成功下載標準學生名單 Excel 範本 (.xlsx)", "success");
  } else {
    // CSV fallback
    const csvContent = "\uFEFF" + "學號,姓名,座號,電子信箱,備註\n" +
      sampleData.map(r => `"${r['學號']}","${r['姓名']}","${r['座號']}","${r['電子信箱']}","${r['備註']}"`).join("\n");
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "課程修課學生名單範本.csv";
    a.click();
    URL.revokeObjectURL(url);
    toast("已下載學生名單 CSV 範本", "info");
  }
}

function handleStudentExcelFile(event) {
  const file = event.target.files && event.target.files[0];
  if (!file) return;
  processStudentFile(file);
}

function processStudentFile(file) {
  const fileName = file.name;
  const fileSizeKB = (file.size / 1024).toFixed(1);
  const ext = fileName.split('.').pop().toLowerCase();

  const reader = new FileReader();

  if (['xlsx', 'xls'].includes(ext)) {
    reader.onload = function (e) {
      try {
        if (typeof XLSX === 'undefined') {
          toast("SheetJS 尚未載入，請改用 CSV 檔案或檢查網路連線", "error");
          return;
        }
        const data = new Uint8Array(e.target.result);
        const workbook = XLSX.read(data, { type: 'array' });
        const firstSheetName = workbook.SheetNames[0];
        const worksheet = workbook.Sheets[firstSheetName];
        const rawJson = XLSX.utils.sheet_to_json(worksheet, { defval: "" });

        parseStudentRows(rawJson, fileName, fileSizeKB);
      } catch (err) {
        console.error("Excel 解析錯誤:", err);
        toast("Excel 檔案解析失敗，請確認檔案格式是否正確", "error");
      }
    };
    reader.readAsArrayBuffer(file);
  } else if (ext === 'csv') {
    reader.onload = function (e) {
      try {
        const text = e.target.result;
        const lines = text.split(/\r\n|\n/).map(l => l.trim()).filter(l => l);
        if (lines.length <= 1) {
          toast("CSV 檔案內容為空或僅有標題列", "error");
          return;
        }
        const headers = lines[0].split(',').map(h => h.replace(/^["']|["']$/g, '').trim());
        const rows = [];
        for (let i = 1; i < lines.length; i++) {
          const cols = lines[i].split(',').map(c => c.replace(/^["']|["']$/g, '').trim());
          const obj = {};
          headers.forEach((h, idx) => { obj[h] = cols[idx] || ""; });
          rows.push(obj);
        }
        parseStudentRows(rows, fileName, fileSizeKB);
      } catch (err) {
        console.error("CSV 解析錯誤:", err);
        toast("CSV 解析失敗", "error");
      }
    };
    reader.readAsText(file, "UTF-8");
  } else {
    toast("請上傳 .xlsx, .xls 或 .csv 格式之學生名單", "error");
  }
}

async function parseStudentRows(rawRows, fileName, fileSizeKB) {
  if (!rawRows || rawRows.length === 0) {
    toast("檔案中未找到任何學生資料列", "warning");
    return;
  }

  let importedStudents = [];
  rawRows.forEach((row, index) => {
    // Flexible header mapping
    const studentNo = row["學號"] || row["studentNo"] || row["StudentNo"] || row["ID"] || row["學籍號碼"] || row["帳號"] || `S${1130000 + index + 1}`;
    const name = row["姓名"] || row["name"] || row["Name"] || row["學生姓名"] || `學生${index + 1}`;
    const seatNo = row["座號"] || row["seatNo"] || row["SeatNo"] || row["號碼"] || String(index + 1).padStart(2, '0');
    const email = row["電子信箱"] || row["信箱"] || row["email"] || row["Email"] || (studentNo ? `${studentNo.toLowerCase()}@school.edu.tw` : '');
    const note = row["備註"] || row["note"] || row["Note"] || row["說明"] || "";

    if (name && name !== "姓名") {
      importedStudents.push({
        id: Date.now() + index,
        studentNo: String(studentNo).trim(),
        name: String(name).trim(),
        seatNo: String(seatNo).trim(),
        email: String(email).trim(),
        note: String(note).trim()
      });
    }
  });

  if (importedStudents.length === 0) {
    toast("未讀取到有效的學生名單資料", "warning");
    return;
  }

  // ── 與資料庫 users 表進行批次比對與核對 ─────────────────────
  toast("正在與資料庫 User 帳號比對名單...", "info");
  const verifyRes = await UserAPI.verifyStudents(importedStudents);
  currentCourseStudents = verifyRes.students;
  renderStudentPreview();

  // Show file info strip
  const fileInfo = document.getElementById('studentFileLoadedInfo');
  const fileNameEl = document.getElementById('studentFileName');
  const fileSizeEl = document.getElementById('studentFileSize');
  if (fileInfo) fileInfo.style.display = 'flex';
  if (fileNameEl) fileNameEl.textContent = fileName;
  if (fileSizeEl) fileSizeEl.textContent = `(${fileSizeKB} KB · 讀取 ${importedStudents.length} 筆 · ${verifyRes.verifiedCount} 筆已核對)`;

  if (verifyRes.unverifiedCount > 0) {
    toast(`已讀取 ${importedStudents.length} 位學生：✅ ${verifyRes.verifiedCount} 位核對成功，⚠️ ${verifyRes.unverifiedCount} 位資料庫查無帳號`, "warning");
  } else {
    toast(`已成功匯入並核對 ${verifyRes.verifiedCount} 位系統學生資料！`, "success");
  }
}

// ── 小老師權限定義 ──────────────────────────────────────────
const TA_PERMISSION_DEFINITIONS = [
  { key: 'q_manage', name: '題庫管理與審核', icon: 'ti-books', color: '#2563eb', desc: '可新增/修改題目、管理單元、匯入考卷與審核 AI 題目' },
  { key: 'exam_manage', name: '測驗發布與組卷', icon: 'ti-file-text', color: '#7c3aed', desc: '可發起課堂測驗、快速組卷、檢視全班作答狀態' },
  { key: 'grade_view', name: '成績與學習分析', icon: 'ti-chart-bar', color: '#059669', desc: '可查閱全班測驗成績分佈、單元薄弱度統計分析' },
  { key: 'hw_manage', name: '作業批改與回饋', icon: 'ti-edit', color: '#d97706', desc: '可檢視學生繳交之作業並給予評語與批閱分數' },
  { key: 'student_manage', name: '點名與學生管理', icon: 'ti-user-check', color: '#0891b2', desc: '可協助課堂點名與登記修課學生備註事項' }
];

function parsePermissionsArray(perms) {
  if (!perms) return [];
  if (Array.isArray(perms)) return perms.filter(Boolean);
  if (typeof perms === 'string') {
    return perms.split(',').map(s => s.trim()).filter(Boolean);
  }
  return [];
}

function setAssistantRole(index) {
  const st = currentCourseStudents[index];
  if (!st) return;
  st.isAssistant = true;
  if (!st.assistantPermissions || parsePermissionsArray(st.assistantPermissions).length === 0) {
    // 預設給予一般助教權限：題庫管理 + 測驗發布
    st.assistantPermissions = 'q_manage,exam_manage';
  }
  renderStudentPreview();
  toast(`已將「${st.name}」指派為小老師！`, "success");
  openAssistantPermissionModal(index);
}

function removeAssistantRole(index) {
  const st = currentCourseStudents[index];
  if (!st) return;
  st.isAssistant = false;
  st.assistantPermissions = '';
  renderStudentPreview();
  toast(`已取消「${st.name}」之小老師身分`, "info");
}

function openAssistantPermissionModal(index) {
  const st = currentCourseStudents[index];
  if (!st) return;

  const curPerms = parsePermissionsArray(st.assistantPermissions);

  const permissionRowsHtml = TA_PERMISSION_DEFINITIONS.map((p, pIdx) => {
    const isChecked = curPerms.includes(p.key);
    return `
      <tr id="taPermRow_${p.key}" style="background:${isChecked ? '#f0fdfa' : '#fff'};border-bottom:1px solid var(--line);transition:background 0.15s;cursor:pointer;" onclick="onTaRowClick('${p.key}', event)">
        <td style="text-align:center;padding:10px 8px;">
          <input type="checkbox" id="taCheck_${p.key}" class="ta-perm-check" value="${p.key}" ${isChecked ? 'checked' : ''} onchange="onTaPermCheckboxChange(this)" style="accent-color:var(--teal-600);width:17px;height:17px;cursor:pointer;">
        </td>
        <td style="padding:10px 12px;">
          <div style="display:flex;align-items:center;gap:8px;font-weight:600;font-size:13.5px;color:var(--ink);">
            <span style="background:${p.color}15;color:${p.color};width:28px;height:28px;border-radius:6px;display:inline-flex;align-items:center;justify-content:center;font-size:15px;">
              <i class="ti ${p.icon}"></i>
            </span>
            ${escapeHtml(p.name)}
          </div>
        </td>
        <td style="padding:10px 12px;font-size:12.5px;color:var(--ink-soft);line-height:1.45;">
          ${escapeHtml(p.desc)}
        </td>
        <td style="text-align:center;padding:10px 8px;">
          <span id="taStatusBadge_${p.key}" class="tag" style="background:${isChecked ? '#ecfdf5' : '#f1f5f9'};color:${isChecked ? '#065f46' : '#64748b'};border:1px solid ${isChecked ? '#a7f3d0' : '#cbd5e1'};font-size:11.5px;padding:2px 8px;border-radius:10px;">
            ${isChecked ? '✓ 已授權' : '未授權'}
          </span>
        </td>
      </tr>
    `;
  }).join('');

  const modalHtml = `
    <div style="display:flex;flex-direction:column;gap:14px;">
      <!-- 小老師基本資料卡片 -->
      <div style="background:linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);border:1px solid #fde68a;border-radius:10px;padding:14px 18px;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;box-shadow:0 1px 3px rgba(245,158,11,0.15);">
        <div style="display:flex;align-items:center;gap:12px;">
          <span style="background:#f59e0b;color:#fff;width:38px;height:38px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-size:18px;box-shadow:0 2px 4px rgba(245,158,11,0.35);">⭐</span>
          <div>
            <div style="font-size:15px;font-weight:700;color:#78350f;display:flex;align-items:center;gap:6px;">
              <span>小老師姓名：</span>
              <span style="font-size:16px;color:#92400e;background:#fff;padding:1px 10px;border-radius:6px;border:1px solid #fbbf24;">${escapeHtml(st.name)}</span>
              <span style="font-size:12.5px;color:#92400e;font-weight:normal;">(${escapeHtml(st.studentNo)})</span>
            </div>
            <div style="font-size:12px;color:#92400e;margin-top:3px;">座號：${escapeHtml(st.seatNo || '-')} · 電子信箱：${escapeHtml(st.email || '-')}</div>
          </div>
        </div>
        <div>
          <label style="display:inline-flex;align-items:center;gap:6px;cursor:pointer;font-size:13.5px;font-weight:700;color:#78350f;background:#fff;padding:6px 12px;border-radius:8px;border:1px solid #fbbf24;box-shadow:0 1px 2px rgba(0,0,0,0.05);">
            <input type="checkbox" id="modalIsAssistantToggle" ${st.isAssistant ? 'checked' : ''} style="accent-color:#f59e0b;width:17px;height:17px;" onchange="onModalIsAssistantToggle(this)"> 擔任本課程小老師
          </label>
        </div>
      </div>

      <!-- 表格式權限設定主區塊 -->
      <div id="taPermConfigBody" style="${st.isAssistant ? '' : 'opacity:0.5;pointer-events:none;'}">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;flex-wrap:wrap;gap:8px;">
          <div style="font-size:13.5px;font-weight:700;color:var(--ink);display:flex;align-items:center;gap:6px;">
            <i class="ti ti-lock" style="color:var(--teal-600);"></i> 小老師功能權限清單（表格配置）：
          </div>
          <div style="display:flex;gap:6px;flex-wrap:wrap;">
            <button type="button" class="ghost" style="padding:3px 10px;font-size:12px;" onclick="setTaPreset('all')">全選</button>
            <button type="button" class="ghost" style="padding:3px 10px;font-size:12px;" onclick="setTaPreset('general')">一般助教 (題庫+測驗)</button>
            <button type="button" class="ghost" style="padding:3px 10px;font-size:12px;" onclick="setTaPreset('affairs')">課務助教 (成績+點名)</button>
            <button type="button" class="ghost" style="padding:3px 10px;font-size:12px;color:var(--red-600);" onclick="setTaPreset('clear')">全部清除</button>
          </div>
        </div>

        <div style="border:1px solid var(--line);border-radius:8px;overflow:hidden;">
          <table style="width:100%;margin:0;border-collapse:collapse;">
            <thead>
              <tr style="background:#f8fafc;border-bottom:2px solid var(--line);">
                <th style="width:55px;text-align:center;">授權</th>
                <th style="width:160px;">功能權限模組</th>
                <th>權限範圍與詳細說明</th>
                <th style="width:95px;text-align:center;">目前狀態</th>
              </tr>
            </thead>
            <tbody id="taPermTableTbody">
              ${permissionRowsHtml}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `;

  window.onTaRowClick = function(key, evt) {
    if (evt.target.tagName === 'INPUT' || evt.target.tagName === 'BUTTON') return;
    const cb = document.getElementById('taCheck_' + key);
    if (cb) {
      cb.checked = !cb.checked;
      window.onTaPermCheckboxChange(cb);
    }
  };

  window.onTaPermCheckboxChange = function(cb) {
    const key = cb.value;
    const row = document.getElementById('taPermRow_' + key);
    const badge = document.getElementById('taStatusBadge_' + key);
    if (row) {
      row.style.background = cb.checked ? '#f0fdfa' : '#fff';
    }
    if (badge) {
      badge.style.background = cb.checked ? '#ecfdf5' : '#f1f5f9';
      badge.style.color = cb.checked ? '#065f46' : '#64748b';
      badge.style.borderColor = cb.checked ? '#a7f3d0' : '#cbd5e1';
      badge.textContent = cb.checked ? '✓ 已授權' : '未授權';
    }
  };

  window.onModalIsAssistantToggle = function(toggle) {
    const body = document.getElementById('taPermConfigBody');
    if (body) {
      body.style.opacity = toggle.checked ? '1' : '0.5';
      body.style.pointerEvents = toggle.checked ? 'auto' : 'none';
    }
  };

  window.setTaPreset = function(type) {
    const checks = document.querySelectorAll('.ta-perm-check');
    checks.forEach(cb => {
      let shouldCheck = false;
      if (type === 'all') shouldCheck = true;
      else if (type === 'general') shouldCheck = (cb.value === 'q_manage' || cb.value === 'exam_manage');
      else if (type === 'affairs') shouldCheck = (cb.value === 'grade_view' || cb.value === 'student_manage');
      else if (type === 'clear') shouldCheck = false;

      cb.checked = shouldCheck;
      window.onTaPermCheckboxChange(cb);
    });
  };

  Modal.open({
    title: `小老師權限設定 — ${st.name}`,
    body: modalHtml,
    confirmText: "儲存權限設定",
    onConfirm: () => {
      const isAss = document.getElementById('modalIsAssistantToggle')?.checked || false;
      const selectedPerms = [];
      document.querySelectorAll('.ta-perm-check:checked').forEach(cb => {
        selectedPerms.push(cb.value);
      });

      st.isAssistant = isAss;
      st.assistantPermissions = isAss ? selectedPerms.join(',') : '';

      renderStudentPreview();
      Modal.close();

      if (isAss) {
        toast(`已更新小老師「${st.name}」的權限設定（已授權 ${selectedPerms.length} 項權限）！`, "success");
      } else {
        toast(`已取消「${st.name}」之小老師身分`, "info");
      }
    }
  });
}

// ── 獨立小老師權限管理頁面 (t-perm) ──────────────────────────
let currentPermCourseId = null;
let currentPermStudentId = null;

async function renderTeacherPermPanel() {
  const container = document.getElementById('teacherPermPanelContainer');
  if (!container) return;

  if (USE_API && (!DB.courses || DB.courses.length === 0)) {
    try {
      const freshCourses = await CourseAPI.list();
      if (freshCourses && freshCourses.length > 0) DB.courses = freshCourses;
    } catch (e) {}
  }

  const courses = DB.courses || [];
  if (courses.length === 0) {
    container.innerHTML = `
      <div class="card" style="text-align:center;padding:48px;color:var(--ink-mute);">
        <i class="ti ti-school-off" style="font-size:36px;display:block;margin-bottom:12px;color:var(--ink-mute);"></i>
        <div style="font-size:16px;font-weight:600;color:var(--ink);margin-bottom:6px;">目前尚無任何課程資料</div>
        <div style="font-size:13px;margin-bottom:16px;">請先至「課程管理」建立課程並匯入學生名單</div>
        <button class="primary" onclick="showPanel('t-course');openCreateCoursePage();">
          <i class="ti ti-plus"></i> 前往建立課程
        </button>
      </div>
    `;
    return;
  }

  // 尋找全系統所有已指派小老師
  let allAssistants = [];
  courses.forEach(c => {
    (c.students || []).forEach((s, sIdx) => {
      if (s.isAssistant) {
        allAssistants.push({ course: c, student: s, studentIdx: sIdx });
      }
    });
  });

  if (!currentPermCourseId || !courses.some(c => c.id === currentPermCourseId)) {
    currentPermCourseId = (allAssistants.length > 0 ? allAssistants[0].course.id : courses[0].id);
  }

  const curCourse = courses.find(c => c.id === currentPermCourseId) || courses[0];
  const courseStudents = curCourse.students || [];
  const courseAssistants = courseStudents.filter(s => s.isAssistant);

  let activeAssistant = null;
  if (courseAssistants.length > 0) {
    activeAssistant = courseAssistants.find(s => (s.id && s.id === currentPermStudentId) || s.studentNo === currentPermStudentId) || courseAssistants[0];
    currentPermStudentId = activeAssistant.id || activeAssistant.studentNo;
  } else {
    currentPermStudentId = null;
  }

  const courseOptionsHtml = courses.map(c => `
    <option value="${c.id}" ${c.id === curCourse.id ? 'selected' : ''}>
      ${escapeHtml(c.name)} (${escapeHtml(c.grade || '高二')} · ${escapeHtml(c.classGroup || '忠班')}) — ${(c.students || []).filter(s => s.isAssistant).length} 位小老師
    </option>
  `).join('');

  let assistantTabsHtml = '';
  if (courseAssistants.length > 0) {
    assistantTabsHtml = `
      <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:16px;background:#fff;padding:10px 14px;border:1px solid var(--line);border-radius:8px;">
        <span style="font-size:13px;font-weight:600;color:var(--ink-soft);display:flex;align-items:center;gap:4px;">
          <i class="ti ti-users" style="color:var(--teal-600);"></i> 選擇小老師：
        </span>
        ${courseAssistants.map(s => {
          const isSelected = (activeAssistant && ((s.id && s.id === activeAssistant.id) || s.studentNo === activeAssistant.studentNo));
          const pCount = parsePermissionsArray(s.assistantPermissions).length;
          return `
            <button type="button" class="${isSelected ? 'primary' : 'ghost'}" onclick="selectPermAssistant('${s.id || s.studentNo}')" style="padding:5px 12px;border-radius:20px;font-size:12.5px;display:inline-flex;align-items:center;gap:6px;">
              <i class="ti ti-star-filled" style="color:${isSelected ? '#fff' : '#f59e0b'};"></i>
              <strong>${escapeHtml(s.name)}</strong>
              <span style="font-size:11px;opacity:0.85;">(${pCount} 項權限)</span>
            </button>
          `;
        }).join('')}
      </div>
    `;
  }

  let bodyContentHtml = '';

  if (activeAssistant) {
    const curPerms = parsePermissionsArray(activeAssistant.assistantPermissions);

    const permissionRowsHtml = TA_PERMISSION_DEFINITIONS.map(p => {
      const isChecked = curPerms.includes(p.key);
      return `
        <tr id="panelPermRow_${p.key}" style="background:${isChecked ? '#f0fdfa' : '#fff'};border-bottom:1px solid var(--line);transition:background 0.15s;cursor:pointer;" onclick="onPanelTaRowClick('${p.key}', event)">
          <td style="text-align:center;padding:12px 8px;">
            <input type="checkbox" id="panelTaCheck_${p.key}" class="panel-ta-perm-check" value="${p.key}" ${isChecked ? 'checked' : ''} onchange="onPanelTaPermCheckboxChange(this)" style="accent-color:var(--teal-600);width:18px;height:18px;cursor:pointer;">
          </td>
          <td style="padding:12px 14px;">
            <div style="display:flex;align-items:center;gap:10px;font-weight:600;font-size:14px;color:var(--ink);">
              <span style="background:${p.color}15;color:${p.color};width:32px;height:32px;border-radius:8px;display:inline-flex;align-items:center;justify-content:center;font-size:16px;">
                <i class="ti ${p.icon}"></i>
              </span>
              ${escapeHtml(p.name)}
            </div>
          </td>
          <td style="padding:12px 14px;font-size:13px;color:var(--ink-soft);line-height:1.5;">
            ${escapeHtml(p.desc)}
          </td>
          <td style="text-align:center;padding:12px 8px;">
            <span id="panelTaStatusBadge_${p.key}" class="tag" style="background:${isChecked ? '#ecfdf5' : '#f1f5f9'};color:${isChecked ? '#065f46' : '#64748b'};border:1px solid ${isChecked ? '#a7f3d0' : '#cbd5e1'};font-size:12px;padding:3px 10px;border-radius:12px;font-weight:600;">
              ${isChecked ? '✓ 已授權' : '未授權'}
            </span>
          </td>
        </tr>
      `;
    }).join('');

    bodyContentHtml = `
      <!-- 小老師個人資訊卡片 -->
      <div style="background:linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);border:1px solid #fde68a;border-radius:12px;padding:16px 20px;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:14px;margin-bottom:16px;box-shadow:0 1px 3px rgba(245,158,11,0.15);">
        <div style="display:flex;align-items:center;gap:14px;">
          <span style="background:#f59e0b;color:#fff;width:44px;height:44px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-size:22px;box-shadow:0 2px 5px rgba(245,158,11,0.35);">⭐</span>
          <div>
            <div style="font-size:16px;font-weight:700;color:#78350f;display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
              <span>小老師姓名：</span>
              <span style="font-size:18px;color:#92400e;background:#fff;padding:2px 14px;border-radius:6px;border:1px solid #fbbf24;box-shadow:0 1px 2px rgba(0,0,0,0.05);">${escapeHtml(activeAssistant.name)}</span>
              <span style="font-size:13px;color:#92400e;font-weight:normal;">(學號：${escapeHtml(activeAssistant.studentNo)})</span>
            </div>
            <div style="font-size:12.5px;color:#92400e;margin-top:4px;">
              所屬課程：<strong>${escapeHtml(curCourse.name)}</strong> (${escapeHtml(curCourse.grade || '高二')} · ${escapeHtml(curCourse.classGroup || '忠班')}) · 座號：${escapeHtml(activeAssistant.seatNo || '-')} · 電子信箱：${escapeHtml(activeAssistant.email || '-')}
            </div>
          </div>
        </div>
        <div style="display:flex;align-items:center;gap:8px;">
          <button type="button" class="ghost danger" onclick="removePermAssistantRole(${curCourse.id}, '${activeAssistant.id || activeAssistant.studentNo}')" style="padding:6px 12px;font-size:12.5px;border-radius:8px;">
            <i class="ti ti-user-x"></i> 取消此小老師身分
          </button>
        </div>
      </div>

      <!-- 表格式權限設定區塊 -->
      <div class="card" style="padding:0;overflow:hidden;margin-bottom:20px;">
        <div style="background:#fff;padding:14px 18px;border-bottom:1px solid var(--line);display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px;">
          <div style="font-size:14px;font-weight:700;color:var(--ink);display:flex;align-items:center;gap:6px;">
            <i class="ti ti-table" style="color:var(--teal-600);font-size:16px;"></i> 小老師功能模組與操作權限表
          </div>
          <div style="display:flex;gap:6px;flex-wrap:wrap;">
            <button type="button" class="ghost" style="padding:4px 10px;font-size:12px;" onclick="setPanelTaPreset('all')">全選</button>
            <button type="button" class="ghost" style="padding:4px 10px;font-size:12px;" onclick="setPanelTaPreset('general')">一般助教 (題庫+測驗)</button>
            <button type="button" class="ghost" style="padding:4px 10px;font-size:12px;" onclick="setPanelTaPreset('affairs')">課務助教 (成績+點名)</button>
            <button type="button" class="ghost" style="padding:4px 10px;font-size:12px;color:var(--red-600);" onclick="setPanelTaPreset('clear')">全部清除</button>
          </div>
        </div>

        <table style="width:100%;margin:0;border-collapse:collapse;">
          <thead>
            <tr style="background:#f8fafc;border-bottom:2px solid var(--line);">
              <th style="width:60px;text-align:center;">授權</th>
              <th style="width:180px;">功能權限模組</th>
              <th>權限範圍與詳細說明</th>
              <th style="width:110px;text-align:center;">授權狀態</th>
            </tr>
          </thead>
          <tbody>
            ${permissionRowsHtml}
          </tbody>
        </table>

        <div style="background:#f8fafc;padding:14px 18px;border-top:1px solid var(--line);display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px;">
          <div style="font-size:12.5px;color:var(--ink-mute);">
            <i class="ti ti-info-circle"></i> 勾選或點擊表格列即可即時切換權限，設定完成後請點擊右方按鈕儲存。
          </div>
          <button type="button" class="primary" onclick="savePanelAssistantPerms(${curCourse.id}, '${activeAssistant.id || activeAssistant.studentNo}')" style="padding:8px 20px;font-size:13.5px;">
            <i class="ti ti-check"></i> 儲存「${escapeHtml(activeAssistant.name)}」權限設定
          </button>
        </div>
      </div>
    `;
  } else {
    bodyContentHtml = `
      <div class="card" style="text-align:center;padding:40px;color:var(--ink-mute);">
        <i class="ti ti-user-star" style="font-size:36px;display:block;margin-bottom:10px;color:#f59e0b;"></i>
        <div style="font-size:15px;font-weight:600;color:var(--ink);margin-bottom:6px;">課程「${escapeHtml(curCourse.name)}」目前尚未指派小老師</div>
        <div style="font-size:13px;color:var(--ink-soft);margin-bottom:18px;">您可以從本課程現有名冊中直接指派小老師，或前往課程管理維護學生名單</div>
        
        ${courseStudents.length > 0 ? `
          <div style="max-width:440px;margin:0 auto;display:flex;gap:8px;">
            <select id="quickAssignStudentSelect" style="flex:1;padding:8px 12px;font-size:13px;border-radius:6px;border:1px solid var(--line);">
              ${courseStudents.map(s => `<option value="${s.id || s.studentNo}">${escapeHtml(s.name)} (${escapeHtml(s.studentNo)}) · 座號 ${escapeHtml(s.seatNo || '-')}</option>`).join('')}
            </select>
            <button type="button" class="primary" onclick="quickAssignAssistantInPanel(${curCourse.id})" style="white-space:nowrap;">
              <i class="ti ti-star"></i> 指派為小老師
            </button>
          </div>
        ` : `
          <button class="primary" onclick="openEditCoursePage(${curCourse.id})">
            <i class="ti ti-upload"></i> 前往匯入此課程學生名單
          </button>
        `}
      </div>
    `;
  }

  container.innerHTML = `
    <!-- 頂部切換與選擇列 -->
    <div class="filter-bar" style="background:#fff;padding:12px 16px;border:1px solid var(--line);border-radius:10px;margin-bottom:16px;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;">
      <div style="display:flex;align-items:center;gap:10px;flex:1;min-width:260px;">
        <label style="font-size:13px;font-weight:600;color:var(--ink);white-space:nowrap;">選擇課程：</label>
        <select id="permCourseSelect" onchange="onPermCourseChange(this.value)" style="font-size:13px;padding:6px 12px;border-radius:6px;border:1px solid var(--line);flex:1;max-width:380px;">
          ${courseOptionsHtml}
        </select>
      </div>
      <div>
        <button type="button" class="ghost" onclick="openEditCoursePage(${curCourse.id})" style="padding:6px 12px;font-size:12.5px;">
          <i class="ti ti-edit"></i> 編輯此課程學生名冊
        </button>
      </div>
    </div>

    ${assistantTabsHtml}
    ${bodyContentHtml}
  `;
}

function onPermCourseChange(courseId) {
  currentPermCourseId = Number(courseId);
  currentPermStudentId = null;
  renderTeacherPermPanel();
}

function selectPermAssistant(studentKey) {
  currentPermStudentId = studentKey;
  renderTeacherPermPanel();
}

function onPanelTaRowClick(key, evt) {
  if (evt.target.tagName === 'INPUT' || evt.target.tagName === 'BUTTON') return;
  const cb = document.getElementById('panelTaCheck_' + key);
  if (cb) {
    cb.checked = !cb.checked;
    onPanelTaPermCheckboxChange(cb);
  }
}

function onPanelTaPermCheckboxChange(cb) {
  const key = cb.value;
  const row = document.getElementById('panelPermRow_' + key);
  const badge = document.getElementById('panelTaStatusBadge_' + key);
  if (row) {
    row.style.background = cb.checked ? '#f0fdfa' : '#fff';
  }
  if (badge) {
    badge.style.background = cb.checked ? '#ecfdf5' : '#f1f5f9';
    badge.style.color = cb.checked ? '#065f46' : '#64748b';
    badge.style.borderColor = cb.checked ? '#a7f3d0' : '#cbd5e1';
    badge.textContent = cb.checked ? '✓ 已授權' : '未授權';
  }
}

function setPanelTaPreset(type) {
  const checks = document.querySelectorAll('.panel-ta-perm-check');
  checks.forEach(cb => {
    let shouldCheck = false;
    if (type === 'all') shouldCheck = true;
    else if (type === 'general') shouldCheck = (cb.value === 'q_manage' || cb.value === 'exam_manage');
    else if (type === 'affairs') shouldCheck = (cb.value === 'grade_view' || cb.value === 'student_manage');
    else if (type === 'clear') shouldCheck = false;

    cb.checked = shouldCheck;
    onPanelTaPermCheckboxChange(cb);
  });
}

async function savePanelAssistantPerms(courseId, studentKey) {
  const course = (DB.courses || []).find(c => c.id === courseId);
  if (!course) return;

  const st = (course.students || []).find(s => (s.id && String(s.id) === String(studentKey)) || String(s.studentNo) === String(studentKey));
  if (!st) return;

  const selectedPerms = [];
  document.querySelectorAll('.panel-ta-perm-check:checked').forEach(cb => {
    selectedPerms.push(cb.value);
  });

  st.isAssistant = true;
  st.assistantPermissions = selectedPerms.join(',');

  try {
    if (USE_API) {
      await CourseAPI.update(courseId, course);
    }
    try { localStorage.setItem('qb_courses', JSON.stringify(DB.courses)); } catch (e) {}
    toast(`已成功儲存小老師「${st.name}」的權限設定（授權 ${selectedPerms.length} 項）！`, "success");
    renderTeacherPermPanel();
  } catch (err) {
    console.error("儲存權限失敗:", err);
    toast(`小老師權限更新完成（本地快取同步）`, "info");
    renderTeacherPermPanel();
  }
}

async function removePermAssistantRole(courseId, studentKey) {
  const course = (DB.courses || []).find(c => c.id === courseId);
  if (!course) return;

  const st = (course.students || []).find(s => (s.id && String(s.id) === String(studentKey)) || String(s.studentNo) === String(studentKey));
  if (!st) return;

  Modal.open({
    title: "確認取消小老師身分",
    body: `確定要取消學生「<strong>${escapeHtml(st.name)}</strong>」的小老師職務嗎？取消後將收回其所有管理權限。`,
    confirmText: "取消小老師",
    confirmClass: "danger",
    onConfirm: async () => {
      st.isAssistant = false;
      st.assistantPermissions = '';

      try {
        if (USE_API) {
          await CourseAPI.update(courseId, course);
        }
        try { localStorage.setItem('qb_courses', JSON.stringify(DB.courses)); } catch (e) {}
        toast(`已取消「${st.name}」之小老師身分`, "info");
        currentPermStudentId = null;
        renderTeacherPermPanel();
        Modal.close();
      } catch (err) {
        toast(`已取消「${st.name}」之小老師身分`, "info");
        renderTeacherPermPanel();
        Modal.close();
      }
    }
  });
}

async function quickAssignAssistantInPanel(courseId) {
  const course = (DB.courses || []).find(c => c.id === courseId);
  if (!course) return;

  const selectEl = document.getElementById('quickAssignStudentSelect');
  const studentKey = selectEl ? selectEl.value : null;
  if (!studentKey) return;

  const st = (course.students || []).find(s => (s.id && String(s.id) === String(studentKey)) || String(s.studentNo) === String(studentKey));
  if (!st) return;

  st.isAssistant = true;
  if (!st.assistantPermissions) {
    st.assistantPermissions = 'q_manage,exam_manage';
  }

  try {
    if (USE_API) {
      await CourseAPI.update(courseId, course);
    }
    try { localStorage.setItem('qb_courses', JSON.stringify(DB.courses)); } catch (e) {}
    toast(`已成功指派「${st.name}」為課程小老師！`, "success");
    currentPermStudentId = st.id || st.studentNo;
    renderTeacherPermPanel();
  } catch (err) {
    toast(`已指派「${st.name}」為小老師`, "info");
    currentPermStudentId = st.id || st.studentNo;
    renderTeacherPermPanel();
  }
}

function renderStudentPreview() {
  const tbody = document.getElementById('studentPreviewTbody');
  const badge = document.getElementById('studentCountBadge');
  const statBadge = document.getElementById('studentVerifyStatBadge');
  const cleanBtn = document.getElementById('cleanUnverifiedBtn');

  const total = currentCourseStudents.length;
  const verifiedCount = currentCourseStudents.filter(s => s.verified).length;
  const unverifiedCount = total - verifiedCount;

  // 小老師統計摘要
  const taList = currentCourseStudents.filter(s => s.isAssistant);
  const taSummaryBar = document.getElementById('taSummaryBar');
  const taCountBadge = document.getElementById('taCountBadge');
  const taNamesChips = document.getElementById('taNamesChips');

  if (taSummaryBar) {
    if (taList.length > 0) {
      taSummaryBar.style.display = 'flex';
      if (taCountBadge) taCountBadge.textContent = `${taList.length} 位`;
      if (taNamesChips) {
        taNamesChips.innerHTML = taList.map((st, tIdx) => {
          const pList = parsePermissionsArray(st.assistantPermissions);
          const pLabels = pList.map(k => {
            const def = TA_PERMISSION_DEFINITIONS.find(d => d.key === k);
            return def ? def.name.slice(0, 4) : k;
          }).join(' · ');
          return `
            <span class="tag" style="background:#fff;border:1px solid #fbbf24;color:#78350f;font-size:11.5px;padding:2px 8px;border-radius:12px;display:inline-flex;align-items:center;gap:4px;">
              <i class="ti ti-star-filled" style="color:#f59e0b;font-size:11px;"></i>
              <strong>${escapeHtml(st.name)}</strong>
              <span style="color:#92400e;font-size:10.5px;">(${pLabels || '未配置權限'})</span>
            </span>
          `;
        }).join('');
      }
    } else {
      taSummaryBar.style.display = 'none';
    }
  }

  if (badge) {
    badge.textContent = `已匯入 ${total} 人`;
    badge.style.background = total > 0 ? 'var(--teal-50)' : 'var(--line)';
    badge.style.color = total > 0 ? 'var(--teal-800)' : 'var(--ink-mute)';
  }

  if (statBadge) {
    if (total > 0) {
      statBadge.style.display = 'inline-block';
      statBadge.innerHTML = `<span style="color:#059669;margin-left:6px;"><i class="ti ti-circle-check-filled"></i> ${verifiedCount} 位已驗證</span>` +
        (unverifiedCount > 0 ? `<span style="color:#dc2626;margin-left:6px;"><i class="ti ti-alert-triangle-filled"></i> ${unverifiedCount} 位查無帳號</span>` : '');
    } else {
      statBadge.style.display = 'none';
    }
  }

  if (cleanBtn) {
    cleanBtn.style.display = unverifiedCount > 0 ? 'inline-flex' : 'none';
  }

  if (!tbody) return;

  if (total === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="9" style="text-align:center;color:var(--ink-mute);padding:28px;">
          <i class="ti ti-users-minus" style="font-size:24px;display:block;margin-bottom:6px;color:var(--ink-mute);"></i>
          尚未匯入任何學生名單。請由上方上傳 Excel 檔案或點擊「自資料庫選取 / 新增學生」
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = currentCourseStudents.map((st, idx) => {
    const isVerified = st.verified !== false;
    const statusHtml = isVerified
      ? `<span class="tag" style="background:#ecfdf5;color:#065f46;border:1px solid #a7f3d0;font-size:11.5px;padding:2px 8px;border-radius:12px;display:inline-flex;align-items:center;gap:4px;" title="${escapeHtml(st.statusMessage || '已核對到系統學生帳號')}"><i class="ti ti-circle-check-filled" style="color:#059669;"></i> 系統學生</span>`
      : `<span class="tag" style="background:#fef2f2;color:#991b1b;border:1px solid #fecaca;font-size:11.5px;padding:2px 8px;border-radius:12px;display:inline-flex;align-items:center;gap:4px;" title="${escapeHtml(st.statusMessage || '資料庫 user 表無此學號/姓名')}"><i class="ti ti-alert-triangle-filled" style="color:#dc2626;"></i> 查無此帳號</span>`;

    const isTa = !!st.isAssistant;
    const pList = parsePermissionsArray(st.assistantPermissions);
    let taRoleHtml = '';
    if (isTa) {
      const chips = pList.map(k => {
        const def = TA_PERMISSION_DEFINITIONS.find(d => d.key === k);
        return `<span style="background:#fef3c7;color:#92400e;border:1px solid #fde68a;font-size:10.5px;padding:1px 6px;border-radius:4px;white-space:nowrap;">${escapeHtml(def ? def.name.slice(0, 4) : k)}</span>`;
      }).join('');

      taRoleHtml = `
        <div style="display:flex;flex-direction:column;gap:3px;align-items:center;">
          <div style="display:flex;align-items:center;gap:4px;">
            <span class="tag" style="background:#fffbeb;color:#92400e;border:1px solid #fde68a;font-weight:700;font-size:11px;padding:2px 6px;border-radius:12px;display:inline-flex;align-items:center;gap:3px;">
              <i class="ti ti-star-filled" style="color:#f59e0b;"></i> 小老師
            </span>
            <button type="button" class="table-action-btn" onclick="openAssistantPermissionModal(${idx})" title="設定權限" style="padding:2px 6px;font-size:11px;color:#d97706;background:#fff8e6;border:1px solid #fde68a;border-radius:4px;">
              <i class="ti ti-settings"></i> 權限 (${pList.length})
            </button>
            <button type="button" class="table-action-btn danger" onclick="removeAssistantRole(${idx})" title="取消小老師" style="padding:2px 4px;font-size:11px;">
              <i class="ti ti-x"></i>
            </button>
          </div>
          <div style="display:flex;gap:3px;flex-wrap:wrap;justify-content:center;max-width:200px;">
            ${chips || '<span style="color:var(--ink-mute);font-size:10.5px;">(未設定權限)</span>'}
          </div>
        </div>
      `;
    } else {
      taRoleHtml = `
        <button type="button" class="ghost" onclick="setAssistantRole(${idx})" style="padding:2px 8px;font-size:11.5px;border-radius:12px;color:var(--ink-soft);border:1px dashed var(--line);background:transparent;" title="指派此學生為小老師">
          <i class="ti ti-star" style="color:#f59e0b;"></i> 設為小老師
        </button>
      `;
    }

    return `
      <tr style="${isTa ? 'background:#fffdfa;' : (isVerified ? '' : 'background:#fffbfb;')}">
        <td style="color:var(--ink-mute);font-size:12px;text-align:center;">${idx + 1}</td>
        <td><span class="code-badge">${escapeHtml(st.studentNo)}</span></td>
        <td style="font-weight:500;color:var(--ink);">${escapeHtml(st.name)}</td>
        <td style="color:var(--ink-soft);">${escapeHtml(st.seatNo || '-')}</td>
        <td style="color:var(--ink-soft);font-size:12px;">${escapeHtml(st.email || '-')}</td>
        <td style="text-align:center;">${statusHtml}</td>
        <td style="text-align:center;">${taRoleHtml}</td>
        <td style="color:var(--ink-mute);font-size:12px;">${escapeHtml(st.note || '-')}</td>
        <td style="text-align:center;">
          <button type="button" class="table-action-btn danger" style="padding:2px 6px;" onclick="removeStudentPreview(${idx})" title="移除此學生">
            <i class="ti ti-trash"></i>
          </button>
        </td>
      </tr>
    `;
  }).join('');
}

function removeUnverifiedStudents() {
  const originalCount = currentCourseStudents.length;
  currentCourseStudents = currentCourseStudents.filter(s => s.verified !== false);
  const removed = originalCount - currentCourseStudents.length;
  renderStudentPreview();
  toast(`已移除 ${removed} 位資料庫中未註冊的學生，保留 ${currentCourseStudents.length} 位有效學生`, "info");
}

function removeStudentPreview(index) {
  currentCourseStudents.splice(index, 1);
  renderStudentPreview();
  toast("已移除該學生", "info");
}

function clearCourseStudents() {
  if (currentCourseStudents.length === 0) return;
  Modal.open({
    title: "確認清空學生名單",
    body: `確定要清空目前已匯入的 ${currentCourseStudents.length} 位學生名單嗎？`,
    confirmText: "清空",
    confirmClass: "danger",
    onConfirm: () => {
      currentCourseStudents = [];
      const fileInfo = document.getElementById('studentFileLoadedInfo');
      if (fileInfo) fileInfo.style.display = 'none';
      const fileInput = document.getElementById('studentExcelInput');
      if (fileInput) fileInput.value = '';
      renderStudentPreview();
      Modal.close();
      toast("已清空學生名單", "info");
    }
  });
}

async function promptAddStudentManual() {
  let dbStudents = [];
  try {
    dbStudents = await UserAPI.getStudents();
  } catch (e) {}

  const studentOptionsHtml = (dbStudents && dbStudents.length > 0)
    ? dbStudents.map(u => {
        const grade = (u.GradeLevel || u.grade || '').trim();
        const cls = (u.ClassName || u.classGroup || '').trim();
        const name = (u.FullName || u.name || '').trim();
        const studentNo = (u.StudentNo || u.username || u.name || '').trim();
        const seat = (u.SeatNumber || u.seatNo || '').trim();
        const email = (u.Email || u.email || '').trim();

        // 格式化顯示為：班級姓名（例如：A班王曉明，不顯示 GradeLevel）
        let displayLabel = u.displayName;
        if (!displayLabel) {
          if (cls || name) {
            displayLabel = `${cls}${name}`;
          } else {
            displayLabel = studentNo || '未命名學生';
          }
        }

        const optValue = studentNo || name;
        return `<option value="${escapeHtml(optValue)}" data-name="${escapeHtml(name || optValue)}" data-grade="${escapeHtml(grade)}" data-class="${escapeHtml(cls)}" data-seat="${escapeHtml(seat)}" data-email="${escapeHtml(email)}">${escapeHtml(displayLabel)}</option>`;
      }).join('')
    : '<option value="">(目前資料庫中無學生資料)</option>';

  const formHtml = `
    <div style="display:flex;flex-direction:column;gap:12px;">
      <div class="form-group" style="margin-bottom:0;background:var(--bg-page);padding:10px;border-radius:6px;border:1px solid var(--line);">
        <label style="font-size:12px;color:var(--teal-800);font-weight:600;display:flex;align-items:center;gap:4px;">
          <i class="ti ti-database"></i> 從資料庫 User 帳號快速選取
        </label>
        <select id="manualDbStudentSelect" onchange="onSelectDbStudent(this)" style="margin-top:4px;font-size:13px;">
          <option value="">-- 請選擇資料庫已存在之學生 (班級姓名) --</option>
          ${studentOptionsHtml}
        </select>
      </div>

      <div class="form-group" style="margin-bottom:0;">
        <label><span style="color:var(--red-600);">*</span> 學號（系統將自動比對資料庫）</label>
        <input type="text" id="manualStudentNo" placeholder="例如：S1130108" required>
      </div>
      <div class="form-group" style="margin-bottom:0;">
        <label><span style="color:var(--red-600);">*</span> 姓名</label>
        <input type="text" id="manualStudentName" placeholder="例如：王曉明" required>
      </div>
      <div class="form-row">
        <div class="form-group" style="margin-bottom:0;">
          <label>座號</label>
          <input type="text" id="manualStudentSeat" placeholder="例如：08">
        </div>
        <div class="form-group" style="margin-bottom:0;">
          <label>備註</label>
          <input type="text" id="manualStudentNote" placeholder="例如：轉學生">
        </div>
      </div>
      <div class="form-group" style="margin-bottom:0;">
        <label>電子信箱</label>
        <input type="text" id="manualStudentEmail" placeholder="例如：s1130108@school.edu.tw">
      </div>
    </div>
  `;

  window.onSelectDbStudent = function(sel) {
    const opt = sel.options[sel.selectedIndex];
    if (opt && opt.value) {
      document.getElementById('manualStudentNo').value = opt.value;
      document.getElementById('manualStudentName').value = opt.dataset.name || '';
      document.getElementById('manualStudentSeat').value = opt.dataset.seat || '';
      document.getElementById('manualStudentEmail').value = opt.dataset.email || '';
    }
  };

  Modal.open({
    title: "自資料庫選取或手動加入學生",
    body: formHtml,
    confirmText: "加入名單並驗證",
    onConfirm: async () => {
      const sNo = document.getElementById('manualStudentNo').value.trim();
      const sName = document.getElementById('manualStudentName').value.trim();
      const sSeat = document.getElementById('manualStudentSeat').value.trim();
      const sEmail = document.getElementById('manualStudentEmail').value.trim();
      const sNote = document.getElementById('manualStudentNote').value.trim();

      if (!sNo && !sName) {
        toast("請輸入學號或姓名", "warning");
        return;
      }

      const tempStudent = {
        id: Date.now(),
        studentNo: sNo,
        name: sName,
        seatNo: sSeat || String(currentCourseStudents.length + 1).padStart(2, '0'),
        email: sEmail || (sNo ? `${sNo.toLowerCase()}@school.edu.tw` : ''),
        note: sNote
      };

      // 即時向資料庫驗證
      const verifyRes = await UserAPI.verifyStudents([tempStudent]);
      const finalStudent = (verifyRes.students && verifyRes.students.length > 0) ? verifyRes.students[0] : tempStudent;

      currentCourseStudents.push(finalStudent);
      renderStudentPreview();
      Modal.close();

      if (finalStudent.verified) {
        toast(`學生「${finalStudent.name}」已確認存在於資料庫 User 表並加入名單！`, "success");
      } else {
        toast(`學生「${finalStudent.name}」已加入（提醒：資料庫 user 表查無此帳號）`, "warning");
      }
    }
  });
}

async function saveCourseForm() {
  const getVal = id => { const el = document.getElementById(id); return el ? el.value.trim() : ''; };

  const name = getVal('courseName');
  const grade = getVal('courseGrade');
  const classGroup = getVal('courseClass') || '忠班';
  const teacher = document.getElementById('courseTeacher')?.value?.trim() || getCurrentTeacherName();

  // Validations
  if (!name) { toast("請輸入課程名稱", "warning"); document.getElementById('courseName')?.focus(); return; }
  if (!grade) { toast("請輸入年級", "warning"); document.getElementById('courseGrade')?.focus(); return; }

  const existingCourse = currentEditingCourseId ? (DB.courses || []).find(c => c.id === currentEditingCourseId) : null;
  const code = existingCourse ? existingCourse.code : ('CRS-' + String(Date.now()).slice(-6));
  const type = existingCourse ? (existingCourse.type || '必修') : '必修';
  const year = existingCourse ? (existingCourse.year || '113') : '113';
  const semester = existingCourse ? (existingCourse.semester || '1') : '1';
  const credits = existingCourse ? (existingCourse.credits || '3') : '3';

  const courseData = {
    name: name,
    code: code,
    type: type,
    year: year,
    semester: semester,
    credits: credits,
    grade: grade,
    classGroup: classGroup,
    teacher: teacher,
    students: currentCourseStudents.map(s => ({
      studentNo: s.studentNo,
      name: s.name,
      seatNo: s.seatNo || '',
      email: s.email || '',
      note: s.note || '',
      isAssistant: s.isAssistant || false,
      assistantPermissions: Array.isArray(s.assistantPermissions) ? s.assistantPermissions.join(',') : (s.assistantPermissions || '')
    }))
  };

  if (currentEditingCourseId) {
    // ── 編輯模式 ──────────────────────────────────────────
    try {
      const updated = await CourseAPI.update(currentEditingCourseId, courseData);
      const idx = DB.courses.findIndex(c => c.id === currentEditingCourseId);
      if (idx >= 0) {
        DB.courses[idx] = updated;
      } else {
        DB.courses.unshift(updated);
      }
      try { localStorage.setItem('qb_courses', JSON.stringify(DB.courses)); } catch (e) { }
      toast(`課程「${name}」已成功更新並寫入資料庫！`, "success");
      closeCreateCoursePage();
    } catch (err) {
      console.error("更新課程失敗:", err);
      const idx = DB.courses.findIndex(c => c.id === currentEditingCourseId);
      if (idx >= 0) {
        DB.courses[idx] = { ...DB.courses[idx], ...courseData, id: currentEditingCourseId };
      }
      try { localStorage.setItem('qb_courses', JSON.stringify(DB.courses)); } catch (e) { }
      toast(`課程「${name}」更新完成（本地端同步）`, "info");
      closeCreateCoursePage();
    }
  } else {
    // ── 新增模式 ──────────────────────────────────────────
    try {
      const saved = await CourseAPI.create(courseData);
      DB.courses = DB.courses.filter(c => c.id !== saved.id);
      DB.courses.unshift(saved);
      try {
        localStorage.setItem('qb_courses', JSON.stringify(DB.courses));
      } catch (e) { }

      toast(`課程「${name}」已成功建立並寫入資料庫！共匯入 ${saved.students ? saved.students.length : courseData.students.length} 名修課學生。`, "success");
      closeCreateCoursePage();
    } catch (err) {
      console.error("寫入資料庫失敗，切換至本地儲存模式:", err);
      const fallbackCourse = {
        ...courseData,
        id: Date.now(),
        createdAt: new Date().toISOString().split('T')[0]
      };
      DB.courses.unshift(fallbackCourse);
      try {
        localStorage.setItem('qb_courses', JSON.stringify(DB.courses));
      } catch (e) { }
      toast(`已將課程「${name}」暫存於前端（後端離線）：共 ${fallbackCourse.students.length} 名修課學生。`, "warning");
      closeCreateCoursePage();
    }
  }
}

function filterCourseList() {
  renderCourseList();
}

function renderCourseList() {
  const keyword = (document.getElementById('courseSearchInput')?.value || '').trim().toLowerCase();
  const filterClass = document.getElementById('courseFilterClass')?.value || '';

  let list = DB.courses || [];
  if (keyword) {
    list = list.filter(c =>
      (c.name && c.name.toLowerCase().includes(keyword)) ||
      (c.grade && c.grade.toLowerCase().includes(keyword)) ||
      (c.classGroup && c.classGroup.toLowerCase().includes(keyword)) ||
      (c.teacher && c.teacher.toLowerCase().includes(keyword))
    );
  }
  if (filterClass) {
    list = list.filter(c => (c.classGroup || '').includes(filterClass));
  }

  // Render teacher table
  const tbody = document.getElementById('courseTableBody');
  if (tbody) {
    if (list.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="7" style="text-align:center;color:var(--ink-mute);padding:36px;">
            <i class="ti ti-school-off" style="font-size:28px;display:block;margin-bottom:8px;color:var(--ink-mute);"></i>
            查無符合條件的課程資料。可點擊上方「新增課程」建立課程
          </td>
        </tr>
      `;
    } else {
      tbody.innerHTML = list.map((c, idx) => {
        const studentList = c.students || [];
        const taList = studentList.filter(s => s.isAssistant);
        const taCount = taList.length;

        return `
          <tr>
            <td style="text-align:center;color:var(--ink-mute);font-size:12.5px;">${idx + 1}</td>
            <td>
              <div style="font-weight:600;font-size:14px;color:var(--ink);">${escapeHtml(c.name)}</div>
            </td>
            <td>
              <span style="font-weight:600;color:var(--ink);">${escapeHtml(c.grade || '高二')}</span>
              <span class="tag" style="background:#f1f5f9;color:#334155;border:1px solid #cbd5e1;font-size:11.5px;padding:2px 8px;border-radius:10px;margin-left:4px;">
                ${escapeHtml(c.classGroup || '忠班')}
              </span>
            </td>
            <td>
              <div style="color:var(--ink);font-weight:500;display:inline-flex;align-items:center;gap:4px;">
                <i class="ti ti-user" style="color:var(--teal-600);font-size:13px;"></i> ${escapeHtml(c.teacher || '王大明')}
              </div>
            </td>
            <td>
              <div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap;">
                <span class="student-count-chip" onclick="viewCourseStudents(${c.id})" title="點擊檢視修課學生名冊">
                  <i class="ti ti-users"></i> ${studentList.length} 人
                </span>
                ${taCount > 0 ? `
                  <span class="tag" onclick="viewCourseStudents(${c.id})" style="background:#fef3c7;color:#92400e;border:1px solid #fde68a;font-size:11px;font-weight:600;padding:2px 7px;border-radius:10px;cursor:pointer;" title="本課程有 ${taCount} 位小老師 (${taList.map(t => t.name).join('、')})">
                    <i class="ti ti-star-filled" style="color:#f59e0b;"></i> ${taCount} 小老師
                  </span>
                ` : ''}
              </div>
            </td>
            <td style="text-align:center;">
              <span class="tag" style="background:#ecfdf5;color:#065f46;border:1px solid #a7f3d0;font-size:11.5px;padding:2px 8px;border-radius:12px;display:inline-flex;align-items:center;gap:3px;">
                <i class="ti ti-circle-check-filled" style="color:#059669;font-size:11px;"></i> ${escapeHtml(c.status || '啟用中')}
              </span>
            </td>
            <td style="text-align:right;white-space:nowrap;">
              <button class="table-action-btn" onclick="openEditCoursePage(${c.id})" style="margin-right:6px;" title="編輯課程資訊">
                <i class="ti ti-edit"></i> 編輯
              </button>
              <button class="table-action-btn" onclick="viewCourseStudents(${c.id})" style="margin-right:6px;" title="檢視學生名單">
                <i class="ti ti-list-details"></i> 名冊
              </button>
              <button class="table-action-btn danger" onclick="deleteCourse(${c.id})" title="刪除課程">
                <i class="ti ti-trash"></i>
              </button>
            </td>
          </tr>
        `;
      }).join('');
    }
  }

  // Render admin table
  const adminTbody = document.getElementById('adminCourseTableBody');
  if (adminTbody) {
    if (list.length === 0) {
      adminTbody.innerHTML = `
        <tr>
          <td colspan="7" style="text-align:center;color:var(--ink-mute);padding:32px;">
            <i class="ti ti-school-off" style="font-size:24px;display:block;margin-bottom:6px;"></i>目前尚無課程資料
          </td>
        </tr>
      `;
    } else {
      adminTbody.innerHTML = list.map((c, idx) => {
        const studentList = c.students || [];
        const taCount = studentList.filter(s => s.isAssistant).length;
        return `
          <tr>
            <td style="text-align:center;color:var(--ink-mute);font-size:12.5px;">${idx + 1}</td>
            <td style="font-weight:600;color:var(--ink);">${escapeHtml(c.name)}</td>
            <td>
              <span style="font-weight:600;">${escapeHtml(c.grade || '高二')}</span>
              <span class="tag" style="background:#f1f5f9;color:#334155;border:1px solid #cbd5e1;font-size:11.5px;padding:2px 8px;border-radius:10px;margin-left:4px;">
                ${escapeHtml(c.classGroup || '忠班')}
              </span>
            </td>
            <td>${escapeHtml(c.teacher || '王大明')}</td>
            <td>
              <div style="display:flex;align-items:center;gap:6px;">
                <span class="student-count-chip" onclick="viewCourseStudents(${c.id})">
                  <i class="ti ti-users"></i> ${studentList.length} 人
                </span>
                ${taCount > 0 ? `<span class="tag" style="background:#fef3c7;color:#92400e;border:1px solid #fde68a;font-size:11px;font-weight:600;padding:1px 6px;border-radius:10px;"><i class="ti ti-star-filled" style="color:#f59e0b;"></i> ${taCount}</span>` : ''}
              </div>
            </td>
            <td style="text-align:center;">
              <span class="tag" style="background:#ecfdf5;color:#065f46;border:1px solid #a7f3d0;font-size:11.5px;padding:2px 8px;border-radius:12px;">
                ${escapeHtml(c.status || '啟用中')}
              </span>
            </td>
            <td style="text-align:right;white-space:nowrap;">
              <button class="table-action-btn" onclick="openEditCoursePage(${c.id})" style="margin-right:6px;" title="編輯課程"><i class="ti ti-edit"></i> 編輯</button>
              <button class="table-action-btn danger" onclick="deleteCourse(${c.id})" title="刪除課程"><i class="ti ti-trash"></i></button>
            </td>
          </tr>
        `;
      }).join('');
    }
  }
}

async function viewCourseStudents(courseId) {
  let course = (DB.courses || []).find(c => c.id === courseId);
  if (!course) return;

  // Try fetching fresh students from backend
  if (USE_API && courseId) {
    try {
      const freshStudents = await CourseAPI.getStudents(courseId);
      if (freshStudents && freshStudents.length > 0) {
        course.students = freshStudents;
      }
    } catch (e) { }
  }

  const students = course.students || [];
  const taList = students.filter(s => s.isAssistant);
  const taCount = taList.length;

  let modalBody = `
    <div style="margin-bottom:14px;display:flex;justify-content:space-between;align-items:center;background:var(--bg-page);padding:10px 14px;border-radius:8px;flex-wrap:wrap;gap:8px;">
      <div>
        <strong style="font-size:15px;color:var(--ink);">${escapeHtml(course.name)}</strong>
        <span style="font-size:12.5px;color:var(--ink-mute);margin-left:8px;">(${escapeHtml(course.grade || '高二')} · ${escapeHtml(course.classGroup || '忠班')} · 授課教師：${escapeHtml(course.teacher || '王大明')})</span>
      </div>
      <div style="display:flex;align-items:center;gap:6px;">
        <span class="fk-badge">${students.length} 位修課學生</span>
        ${taCount > 0 ? `
          <span class="tag" style="background:#fef3c7;color:#92400e;border:1px solid #fde68a;font-weight:700;font-size:12px;padding:3px 10px;border-radius:12px;display:inline-flex;align-items:center;gap:4px;">
            <i class="ti ti-star-filled" style="color:#f59e0b;"></i> ${taCount} 位小老師
          </span>
        ` : ''}
      </div>
    </div>
  `;

  if (taCount > 0) {
    modalBody += `
      <div style="background:linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);border:1px solid #fde68a;border-radius:8px;padding:10px 14px;margin-bottom:12px;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px;">
        <div style="display:flex;align-items:center;gap:8px;">
          <span style="background:#f59e0b;color:#fff;width:24px;height:24px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-size:12px;">⭐</span>
          <span style="font-size:13px;font-weight:600;color:#92400e;">小老師名冊：</span>
          <div style="display:flex;gap:6px;flex-wrap:wrap;">
            ${taList.map(t => {
              const pList = parsePermissionsArray(t.assistantPermissions);
              const pLabels = pList.map(k => {
                const def = TA_PERMISSION_DEFINITIONS.find(d => d.key === k);
                return def ? def.name.slice(0, 4) : k;
              }).join('·');
              return `<span class="tag" style="background:#fff;border:1px solid #fbbf24;color:#78350f;font-size:11.5px;padding:1px 8px;border-radius:10px;display:inline-flex;align-items:center;gap:3px;">
                <strong>${escapeHtml(t.name)}</strong>
                <span style="color:#92400e;font-size:10.5px;">(${pLabels || '未設權限'})</span>
              </span>`;
            }).join('')}
          </div>
        </div>
      </div>
    `;
  }

  if (students.length === 0) {
    modalBody += `
      <div style="text-align:center;color:var(--ink-mute);padding:32px;">
        <i class="ti ti-users-minus" style="font-size:24px;display:block;margin-bottom:6px;"></i>本課程目前尚無修課學生名單
      </div>
    `;
  } else {
    modalBody += `
      <div style="max-height:360px;overflow-y:auto;border:1px solid var(--line);border-radius:8px;">
        <table style="margin:0;">
          <thead>
            <tr>
              <th style="width:45px;text-align:center;">序號</th>
              <th>學號</th>
              <th>姓名</th>
              <th>座號</th>
              <th style="text-align:center;min-width:140px;">身分與管理權限</th>
              <th>電子信箱</th>
              <th>備註</th>
            </tr>
          </thead>
          <tbody>
            ${students.map((s, idx) => {
              const isTa = !!s.isAssistant;
              const pList = parsePermissionsArray(s.assistantPermissions);
              let roleHtml = '';
              if (isTa) {
                const chips = pList.map(k => {
                  const def = TA_PERMISSION_DEFINITIONS.find(d => d.key === k);
                  return `<span style="background:#fef3c7;color:#92400e;border:1px solid #fde68a;font-size:10.5px;padding:1px 5px;border-radius:4px;white-space:nowrap;">${escapeHtml(def ? def.name.slice(0, 4) : k)}</span>`;
                }).join('');

                roleHtml = `
                  <div style="display:flex;flex-direction:column;gap:3px;align-items:center;">
                    <span class="tag" style="background:#fffbeb;color:#92400e;border:1px solid #fde68a;font-weight:700;font-size:11px;padding:2px 8px;border-radius:12px;display:inline-flex;align-items:center;gap:3px;box-shadow:0 1px 2px rgba(245,158,11,0.15);">
                      <i class="ti ti-star-filled" style="color:#f59e0b;"></i> 小老師
                    </span>
                    <div style="display:flex;gap:3px;flex-wrap:wrap;justify-content:center;max-width:180px;">
                      ${chips || '<span style="color:var(--ink-mute);font-size:10.5px;">(未設定權限)</span>'}
                    </div>
                  </div>
                `;
              } else {
                roleHtml = `<span style="color:var(--ink-mute);font-size:12px;">一般學生</span>`;
              }

              return `
                <tr style="${isTa ? 'background:#fffdf7;' : ''}">
                  <td style="color:var(--ink-mute);font-size:12px;text-align:center;">${idx + 1}</td>
                  <td><span class="code-badge">${escapeHtml(s.studentNo)}</span></td>
                  <td style="font-weight:${isTa ? '700' : '500'};color:${isTa ? '#92400e' : 'var(--ink)'};">
                    ${isTa ? '<span style="color:#f59e0b;margin-right:3px;">⭐</span>' : ''}${escapeHtml(s.name)}
                  </td>
                  <td>${escapeHtml(s.seatNo || '-')}</td>
                  <td style="text-align:center;">${roleHtml}</td>
                  <td style="font-size:12px;color:var(--ink-soft);">${escapeHtml(s.email || '-')}</td>
                  <td style="font-size:12px;color:var(--ink-mute);">${escapeHtml(s.note || '-')}</td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  Modal.open({
    title: `修課學生名冊 — ${course.name}`,
    body: modalBody,
    hideFooter: true
  });
}

function deleteCourse(courseId) {
  const course = (DB.courses || []).find(c => c.id === courseId);
  if (!course) return;

  Modal.open({
    title: "確認刪除課程",
    body: `確定要從資料庫刪除課程「<strong>${escapeHtml(course.name)}</strong> (${escapeHtml(course.code)})」嗎？此操作將同時移除關聯的學生名冊資料。`,
    confirmText: "刪除課程",
    confirmClass: "danger",
    onConfirm: async () => {
      try {
        await CourseAPI.remove(courseId);
        toast(`課程「${course.name}」已從資料庫中刪除`, "info");
      } catch (err) {
        console.warn("後端刪除失敗，同步刪除本地快取", err);
        toast(`課程「${course.name}」已刪除`, "info");
      }
      DB.courses = DB.courses.filter(c => c.id !== courseId);
      try {
        localStorage.setItem('qb_courses', JSON.stringify(DB.courses));
      } catch (e) { }
      renderCourseList();
      Modal.close();
    }
  });
}

function initStudentDropZone() {
  const dropZone = document.getElementById('studentDropZone');
  if (!dropZone || dropZone._inited) return;
  dropZone._inited = true;

  ['dragenter', 'dragover'].forEach(eventName => {
    dropZone.addEventListener(eventName, e => {
      e.preventDefault();
      e.stopPropagation();
      dropZone.classList.add('dragover');
    }, false);
  });

  ['dragleave', 'drop'].forEach(eventName => {
    dropZone.addEventListener(eventName, e => {
      e.preventDefault();
      e.stopPropagation();
      dropZone.classList.remove('dragover');
    }, false);
  });

  dropZone.addEventListener('drop', e => {
    const dt = e.dataTransfer;
    const files = dt.files;
    if (files && files.length > 0) {
      processStudentFile(files[0]);
    }
  }, false);
}


function switchRole(roleKey) {
  document.querySelectorAll('.role-btn').forEach(b => b.classList.toggle('active', b.dataset.role === roleKey));
  const role = roles[roleKey];
  document.getElementById('userName').textContent = role.name;
  document.getElementById('userAvatar').textContent = role.avatar;
  panelInited = { import: false, bank: false, folder: false };
  renderSidebar(roleKey);
  showPanel(role.default);
  const pN = document.getElementById('profileName');
  const pA = document.getElementById('profileAvatar');
  const pR = document.getElementById('profileRole');
  if (pN) { pN.textContent = role.name; pA.textContent = role.avatar; pR.textContent = roleLabel[roleKey]; }
}

document.getElementById('roleSwitcher').addEventListener('click', e => {
  const btn = e.target.closest('.role-btn');
  if (btn) switchRole(btn.dataset.role);
});

/* ============ Login ============ */
let loginRole = 'teacher';
document.getElementById('loginRoleSelect').addEventListener('click', e => {
  const btn = e.target.closest('button');
  if (!btn) return;
  document.querySelectorAll('#loginRoleSelect button').forEach(b => b.classList.toggle('active', b === btn));
  loginRole = btn.dataset.role;
});
document.getElementById('loginSubmit').addEventListener('click', () => {
  document.getElementById('loginScreen').style.display = 'none';
  document.getElementById('mainApp').style.display = 'flex';
  switchRole(loginRole);
});

/* ============ Profile ============ */
document.getElementById('userChip').addEventListener('click', () => {
  document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
  document.querySelectorAll('.panel').forEach(el => el.classList.remove('active'));
  document.getElementById('profile-panel').classList.add('active');
});

/* Exam panel wiring */
document.getElementById('startExamBtn').addEventListener('click', () => showPanel('s-taking'));
document.querySelectorAll('.exam-option').forEach(opt => {
  opt.addEventListener('click', () => {
    opt.closest('.exam-options').querySelectorAll('.exam-option').forEach(o => o.classList.remove('selected'));
    opt.classList.add('selected');
  });
});

/* Folder upload wiring (deferred until DOM exists) */
/* Global Data Initializer */
async function initAppData() {
  if (USE_API) {
    try {
      const q = await QuestionAPI.list({});
      DB.questions = q;
    } catch (e) { console.warn('無法連線至後端題庫 API', e); }
    try {
      const p = await ImportAPI.listPending();
      if (p && p.data) DB.pending = p.data;
    } catch (e) { }
    try {
      const f = await FolderAPI.list();
      DB.folders = f;
    } catch (e) { }
    try {
      const c = await CourseAPI.list();
      if (c && c.length > 0) DB.courses = c;
    } catch (e) { console.warn('無法連線至後端課程 API', e); }
  }
  buildBankFilterOptions();
  syncDashboard();
  renderCourseList();
}

/* Init */
switchRole('teacher');
initAppData();

