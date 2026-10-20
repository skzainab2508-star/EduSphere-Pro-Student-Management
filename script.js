
/* ===== PREMIUM INTERACTIONS ===== */

// Theme toggle
const themeButton = document.createElement('button');
themeButton.id = 'themeToggle';
themeButton.type = 'button';
themeButton.textContent = '🌙 Dark';
themeButton.title = 'Toggle dark mode';

const topbar = document.querySelector('.topbar');
topbar.querySelector('div:last-child').prepend(themeButton);

themeButton.addEventListener('click', () => {
  document.body.classList.toggle('dark');

  const dark = document.body.classList.contains('dark');
  themeButton.textContent = dark ? '☀️ Light' : '🌙 Dark';
  localStorage.setItem('edusphere-theme', dark ? 'dark' : 'light');
});

if (localStorage.getItem('edusphere-theme') === 'dark') {
  document.body.classList.add('dark');
  themeButton.textContent = '☀️ Light';
}

// Toast notifications
const toast = document.createElement('div');
toast.id = 'toast';
toast.setAttribute('role', 'status');
toast.setAttribute('aria-live', 'polite');
document.body.appendChild(toast);

let toastTimer;

function notify(message) {
  toast.textContent = message;
  toast.classList.add('show');

  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toast.classList.remove('show');
  }, 2800);
}

// Live date and time on dashboard
const clock = document.createElement('div');
clock.className = 'muted';
clock.style.fontSize = '12px';
clock.style.marginTop = '8px';

const heading = document.querySelector('.page-head > div');
heading.appendChild(clock);

function updateClock() {
  clock.textContent = new Date().toLocaleString('en-IN', {
    weekday: 'short',
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
}

updateClock();
setInterval(updateClock, 1000);

// Add an academic insights panel
const insights = document.createElement('div');
insights.className = 'panel';
insights.innerHTML = `
  <div class="panel-head">
    <h2>🏆 Academic Insights</h2>
    <span class="pill">LIVE DATA</span>
  </div>
  <div id="insightContent"></div>
`;

document.querySelector('#overviewTab').appendChild(insights);

function updateInsights() {
  const target = document.getElementById('insightContent');
  if (!target) return;

  if (!records.length) {
    target.textContent = 'Add student records to view academic insights.';
    return;
  }

  const sorted = [...records].sort(
    (a, b) => Number(b.marks || 0) - Number(a.marks || 0)
  );

  const average = records.reduce(
    (sum, s) => sum + Number(s.marks || 0), 0
  ) / records.length;

  const lowAttendance = records.filter(
    s => Number(s.att || 0) < 75
  );

  const topper = sorted[0];

  target.innerHTML = `
    <div class="notice">
      🥇 <b>Top performer:</b>
      ${safe(topper.name)} — ${safe(topper.marks)}/100
    </div>
    <div class="notice">
      📈 <b>Class average:</b> ${average.toFixed(1)}/100
    </div>
    <div class="notice">
      ⚠️ <b>Attendance alerts:</b>
      ${lowAttendance.length} student(s) below 75% attendance
    </div>
    <div class="notice">
      🌟 <b>High achievers:</b>
      ${records.filter(s => Number(s.marks) >= 80).length} student(s) scored 80 or above
    </div>
  `;
}

// Wrap existing render function so insights refresh with the data
const previousRenderAll = renderAll;

renderAll = function() {
  previousRenderAll();
  updateInsights();
};

// Improve feedback after adding or editing a record
const originalSubmitHandler = null;

$('studentForm').addEventListener('submit', () => {
  notify('Saving student record...');
});

// Welcome message after successful login
const originalOpenPortal = openPortal;

openPortal = async function() {
  await originalOpenPortal();
  notify('Welcome to EduSphere Pro ✨');
};
