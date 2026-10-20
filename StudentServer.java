
package com.studentmanagement;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;

public class StudentServer {

    private static final int PORT = 8092;
    private static final List<Map<String, String>> students =
            new ArrayList<>();
    private static int nextId = 1;

    public static void main(String[] args) throws IOException {
        students.add(makeStudent("1", "Aarav Sharma", "101",
                "CSE-A", "aarav@example.com", "88", "95"));
        students.add(makeStudent("2", "Ananya Patil", "102",
                "CSE-A", "ananya@example.com", "92", "98"));
        nextId = 3;

        HttpServer server = HttpServer.create(
                new InetSocketAddress(PORT), 0);

        server.createContext("/", ex -> {
            if (ex.getRequestURI().getPath().equals("/")) {
                send(ex, 200, page(), "text/html; charset=UTF-8");
            } else {
                send(ex, 404, "{\"error\":\"Page not found\"}",
                        "application/json; charset=UTF-8");
            }
        });

        server.createContext("/api/login", StudentServer::login);
        server.createContext("/api/students", StudentServer::studentAPI);

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("====================================");
        System.out.println(" EduSphere Student Management System");
        System.out.println(" Open: http://localhost:" + PORT);
        System.out.println("====================================");
    }

    private static Map<String, String> makeStudent(
            String id, String name, String roll, String cls,
            String email, String marks, String attendance) {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("id", id);
        s.put("name", name);
        s.put("roll", roll);
        s.put("studentClass", cls);
        s.put("email", email);
        s.put("marks", marks);
        s.put("att", attendance);
        return s;
    }

    private static void login(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            send(ex, 405, "{\"error\":\"POST required\"}",
                    "application/json; charset=UTF-8");
            return;
        }

        Map<String, String> form = readForm(ex);
        String username = form.getOrDefault("username", "");
        String password = form.getOrDefault("password", "");
        String role = form.getOrDefault("role", "");

        boolean valid =
                (role.equals("student") &&
                        username.equals("student") &&
                        password.equals("student123"))
                || (role.equals("teacher") &&
                        username.equals("teacher") &&
                        password.equals("teacher123"))
                || (role.equals("parent") &&
                        username.equals("parent") &&
                        password.equals("parent123"));

        if (valid) {
            send(ex, 200,
                    "{\"success\":true,\"role\":\"" + role + "\"}",
                    "application/json; charset=UTF-8");
        } else {
            send(ex, 401,
                    "{\"success\":false,\"error\":\"Incorrect username or password.\"}",
                    "application/json; charset=UTF-8");
        }
    }

    private static synchronized void studentAPI(HttpExchange ex)
            throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers",
                "Content-Type");

        if (method.equals("OPTIONS")) {
            send(ex, 204, "", "text/plain");
            return;
        }

        try {
            if (method.equals("GET")) {
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < students.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(toJson(students.get(i)));
                }
                json.append("]");
                send(ex, 200, json.toString(),
                        "application/json; charset=UTF-8");
                return;
            }

            if (method.equals("POST")) {
                Map<String, String> form = readForm(ex);
                String name = form.getOrDefault("name", "").trim();
                String roll = form.getOrDefault("roll", "").trim();
                String cls = form.getOrDefault("studentClass", "").trim();

                if (name.isEmpty() || roll.isEmpty() || cls.isEmpty()) {
                    send(ex, 400,
                            "{\"error\":\"Name, roll and class are required.\"}",
                            "application/json; charset=UTF-8");
                    return;
                }

                for (Map<String, String> s : students) {
                    if (s.get("roll").equalsIgnoreCase(roll)) {
                        send(ex, 409,
                                "{\"error\":\"Roll number already exists.\"}",
                                "application/json; charset=UTF-8");
                        return;
                    }
                }

                Map<String, String> s = makeStudent(
                        String.valueOf(nextId++), name, roll, cls,
                        form.getOrDefault("email", ""),
                        form.getOrDefault("marks", "0"),
                        form.getOrDefault("att", "100"));

                students.add(s);
                send(ex, 201, toJson(s),
                        "application/json; charset=UTF-8");
                return;
            }

            if (method.equals("PUT")) {
                Map<String, String> form = readForm(ex);
                String id = form.getOrDefault("id", "");
                Map<String, String> s = findStudent(id);

                if (s == null) {
                    send(ex, 404, "{\"error\":\"Student not found.\"}",
                            "application/json; charset=UTF-8");
                    return;
                }

                for (Map<String, String> other : students) {
                    if (!other.get("id").equals(id)
                            && other.get("roll").equalsIgnoreCase(
                            form.getOrDefault("roll", ""))) {
                        send(ex, 409,
                                "{\"error\":\"Roll number already exists.\"}",
                                "application/json; charset=UTF-8");
                        return;
                    }
                }

                updateField(s, form, "name");
                updateField(s, form, "roll");
                updateField(s, form, "studentClass");
                updateField(s, form, "email");
                updateField(s, form, "marks");
                updateField(s, form, "att");

                send(ex, 200, toJson(s),
                        "application/json; charset=UTF-8");
                return;
            }

            if (method.equals("DELETE")) {
                String query = ex.getRequestURI().getRawQuery();
                Map<String, String> params = parseForm(
                        query == null ? "" : query);
                String id = params.getOrDefault("id", "");

                boolean removed = students.removeIf(
                        s -> s.get("id").equals(id));

                if (!removed) {
                    send(ex, 404, "{\"error\":\"Student not found.\"}",
                            "application/json; charset=UTF-8");
                    return;
                }

                send(ex, 200, "{\"success\":true}",
                        "application/json; charset=UTF-8");
                return;
            }

            send(ex, 405, "{\"error\":\"Method not allowed.\"}",
                    "application/json; charset=UTF-8");

        } catch (Exception e) {
            send(ex, 400, "{\"error\":\"Invalid request data.\"}",
                    "application/json; charset=UTF-8");
        }
    }

    private static void updateField(Map<String, String> target,
                                    Map<String, String> source,
                                    String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private static Map<String, String> findStudent(String id) {
        for (Map<String, String> s : students) {
            if (s.get("id").equals(id)) return s;
        }
        return null;
    }

    private static Map<String, String> readForm(HttpExchange ex)
            throws IOException {
        String body;
        try (InputStream in = ex.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return parseForm(body);
    }

    private static Map<String, String> parseForm(String body)
            throws UnsupportedEncodingException {
        Map<String, String> result = new HashMap<>();
        if (body.isEmpty()) return result;

        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], "UTF-8");
            String value = parts.length > 1
                    ? URLDecoder.decode(parts[1], "UTF-8") : "";
            result.put(key, value);
        }
        return result;
    }

    private static String toJson(Map<String, String> s) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, String> entry : s.entrySet()) {
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(jsonEscape(entry.getKey()))
                    .append("\":\"")
                    .append(jsonEscape(entry.getValue())).append("\"");
        }
        return json.append("}").toString();
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void send(HttpExchange ex, int status, String text,
                             String contentType) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        if (status == 204) {
            ex.sendResponseHeaders(status, -1);
            ex.close();
            return;
        }

        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }


private static String page() {
    return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>EduSphere Pro | Smart Campus</title>
<style>
:root{--p:#6754df;--dark:#201b46;--bg:#f5f6fc;--muted:#858ba3;--line:#e9eaf4}
*{box-sizing:border-box}
body{margin:0;font-family:Inter,"Segoe UI",Arial,sans-serif;background:var(--bg);color:var(--dark)}
button,input,select{font:inherit}
button{cursor:pointer;transition:.2s}
button:hover{transform:translateY(-1px);filter:brightness(.97)}
.hidden{display:none!important}
.topbar{height:76px;background:white;display:flex;align-items:center;justify-content:space-between;padding:0 6%;border-bottom:1px solid var(--line)}
.logo{font-size:25px;font-weight:850;color:var(--p);letter-spacing:-1px}
.logo span{color:#f0a34c}
.pill{background:#efedff;color:var(--p);border-radius:30px;padding:9px 14px;font-size:12px;font-weight:800}
.btn{border:0;border-radius:11px;background:var(--p);color:white;padding:12px 17px;font-weight:700}
.btn-light{border:1px solid var(--line);border-radius:10px;background:white;color:var(--dark);padding:11px 15px;font-weight:650}
.btn-danger{background:#fff0f1;color:#c33950;border:0;padding:8px;border-radius:8px}
.hero{padding:75px 8%;display:grid;grid-template-columns:1.1fr .9fr;align-items:center;gap:45px;background:linear-gradient(120deg,#fff 30%,#eeeaff)}
.eyebrow{color:var(--p);font-size:11px;letter-spacing:2px;font-weight:850}
h1{font-size:clamp(40px,5vw,66px);letter-spacing:-2.5px;line-height:1.08;margin:20px 0}
h1 span{color:var(--p)}
p{line-height:1.7}
.muted{color:var(--muted);font-size:14px}
.hero-actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:26px}
.hero-panel{background:white;border:1px solid #eeeafa;border-radius:24px;padding:28px;box-shadow:0 25px 70px #342c7318}
.hero-panel h2{font-size:21px}
.mini-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}
.mini{background:#f7f6ff;padding:18px;border-radius:15px;color:#747995;font-size:12px}
.mini strong{display:block;color:var(--dark);font-size:26px;margin-top:10px}
.section{padding:60px 8%;text-align:center}
.features{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:28px;text-align:left}
.feature{background:white;padding:25px;border:1px solid var(--line);border-radius:18px}
.feature .ico{font-size:28px}
.feature p{font-size:13px;color:var(--muted)}
.feature button{background:none;border:0;color:var(--p);font-weight:800;padding:0}
.login-wrap{min-height:calc(100vh - 76px);display:grid;grid-template-columns:1fr 1fr}
.login-art{padding:9%;background:linear-gradient(145deg,#29204f,#6250dc);color:white;display:flex;flex-direction:column;justify-content:space-between}
.login-art h2{font-size:40px;line-height:1.2}
.login-box{width:min(420px,90%);margin:auto;padding:30px 0}
.login-box h2{font-size:32px;margin-bottom:5px}
.roles{display:grid;grid-template-columns:repeat(3,1fr);gap:7px;padding:5px;background:#efeff8;border-radius:13px;margin:25px 0}
.role{border:0;background:transparent;padding:12px 3px;border-radius:9px;font-size:12px;font-weight:800;color:#777d96}
.role.active{background:white;color:var(--p);box-shadow:0 2px 8px #24204b12}
label{display:block;font-size:12px;font-weight:800;margin:14px 0 7px}
input,select{width:100%;padding:12px 13px;border:1px solid #dedfed;border-radius:10px;background:white;outline:none}
input:focus,select:focus{border-color:var(--p);box-shadow:0 0 0 3px #6754df18}
.login-box .btn{width:100%;margin-top:18px}
.err{color:#c33950;font-size:13px;min-height:18px}
.app{display:grid;grid-template-columns:235px 1fr;min-height:calc(100vh - 76px)}
.sidebar{background:#fff;border-right:1px solid var(--line);padding:25px 16px}
.side-label{font-size:10px;letter-spacing:1.5px;color:#a0a4b9;font-weight:850;margin:22px 12px 10px}
.side-btn{width:100%;text-align:left;border:0;background:transparent;padding:12px;border-radius:10px;color:#72778e;margin:3px 0;font-weight:650}
.side-btn.active,.side-btn:hover{background:#efedff;color:var(--p)}
.main{padding:30px;min-width:0}
.page-head{display:flex;justify-content:space-between;align-items:center;gap:15px;flex-wrap:wrap;margin-bottom:25px}
.page-head h1{font-size:30px;letter-spacing:-1px;margin:8px 0}
.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:15px;margin-bottom:22px}
.metric{background:white;padding:20px;border:1px solid var(--line);border-radius:16px}
.metric .m-icon{font-size:20px}
.metric strong{display:block;font-size:29px;margin:13px 0 5px}
.metric small{color:var(--muted)}
.panel{background:white;border:1px solid var(--line);border-radius:16px;padding:22px;margin-bottom:20px;min-width:0}
.panel-head{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:16px}
.panel-head h2{font-size:17px;margin:0}
.two-col{display:grid;grid-template-columns:1.3fr .7fr;gap:18px}
.bar-row{display:grid;grid-template-columns:90px 1fr 45px;gap:12px;align-items:center;margin:19px 0;font-size:12px}
.bar{height:9px;background:#eeeefa;border-radius:20px;overflow:hidden}
.bar span{height:100%;display:block;background:linear-gradient(90deg,#7d69ed,#4f9de5);border-radius:20px}
.notice{padding:13px;background:#f7f6ff;border-radius:11px;margin:10px 0;font-size:13px}
.form-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}
.table-wrap{overflow:auto}
table{width:100%;border-collapse:collapse;font-size:12px}
th,td{text-align:left;padding:13px 10px;border-bottom:1px solid var(--line);white-space:nowrap}
th{background:#f7f6ff;color:#6e7290;font-size:11px}
.avatar{display:inline-grid;place-items:center;width:34px;height:34px;border-radius:11px;background:#eeebff;color:var(--p);font-weight:850;margin-right:8px}
.tag{display:inline-block;border-radius:30px;padding:5px 9px;background:#e6f8ef;color:#25845a;font-size:11px;font-weight:800}
footer{text-align:center;padding:25px;color:#9297ac;font-size:12px}
@media(max-width:950px){.metrics{grid-template-columns:repeat(2,1fr)}.app{grid-template-columns:180px 1fr}.main{padding:18px}.two-col{grid-template-columns:1fr}}
@media(max-width:650px){.hero,.login-wrap{grid-template-columns:1fr}.hero{padding:45px 7%}.features{grid-template-columns:1fr}.app{grid-template-columns:1fr}.sidebar{display:flex;overflow:auto;padding:8px}.side-label{display:none}.side-btn{white-space:nowrap;width:auto}.main{padding:14px}.form-grid{grid-template-columns:1fr 1fr}.login-art{min-height:220px}.metrics{gap:9px}.metric{padding:13px}.metric strong{font-size:23px}}
</style>
</head>
<body>
<nav class="topbar">
  <div class="logo">EduSphere<span>+</span></div>
  <div style="display:flex;align-items:center;gap:12px"><span class="pill">SMART CAMPUS PLATFORM</span><button class="btn" onclick="showLogin('student')">Sign In ↗</button></div>
</nav>

<section id="landing">
 <div class="hero">
  <div>
   <div class="eyebrow">THE NEXT GENERATION OF EDUCATION</div>
   <h1>Smarter learning.<br><span>Better futures.</span></h1>
   <p class="muted" style="max-width:470px">A unified academic platform connecting students, teachers and parents. Track progress, manage records and celebrate every achievement.</p>
   <div class="hero-actions">
    <button class="btn" onclick="showLogin('student')">Explore Student Portal →</button>
    <button class="btn-light" onclick="showLogin('teacher')">Teacher Login</button>
    <button class="btn-light" onclick="showLogin('parent')">Parent Login</button>
   </div>
   <p class="muted">✦ Academic insights &nbsp; ✦ Attendance tracking &nbsp; ✦ Progress reports</p>
  </div>
  <div class="hero-panel">
   <div class="eyebrow">YOUR CAMPUS AT A GLANCE</div>
   <h2>Welcome to a smarter campus ✨</h2>
   <p class="muted">Everything important, organised in one place.</p>
   <div class="mini-grid">
    <div class="mini">Academic excellence<strong>92%</strong></div>
    <div class="mini">Daily attendance<strong>96%</strong></div>
    <div class="mini">Learning first<strong>24/7</strong></div>
    <div class="mini">Connected roles<strong>3</strong></div>
   </div>
   <div class="notice">💡 <b>Learning tip</b><br>Consistency today builds confidence tomorrow.</div>
  </div>
 </div>
 <div class="section">
  <div class="eyebrow">ONE PLATFORM, THREE EXPERIENCES</div>
  <h2 style="font-size:32px">Designed for everyone in education</h2>
  <div class="features">
   <div class="feature"><div class="ico">🎓</div><h3>Student Workspace</h3><p>View academic marks, attendance, achievements and personal progress.</p><button onclick="showLogin('student')">Open Student Portal →</button></div>
   <div class="feature"><div class="ico">📘</div><h3>Teacher Workspace</h3><p>Manage student records, update marks, monitor attendance and export data.</p><button onclick="showLogin('teacher')">Open Teacher Portal →</button></div>
   <div class="feature"><div class="ico">👨‍👩‍👧</div><h3>Parent Workspace</h3><p>Follow your child's academic progress and attendance at a glance.</p><button onclick="showLogin('parent')">Open Parent Portal →</button></div>
  </div>
 </div>
</section>

<section id="login" class="login-wrap hidden">
 <div class="login-art">
  <button class="btn-light" style="align-self:flex-start" onclick="goHome()">← Back to home</button>
  <div><div class="eyebrow" style="color:#d5ceff">WELCOME TO EDUSPHERE</div><h2>Every achievement starts with a step.</h2><p style="color:#e0dcff">Your academic journey, beautifully organised.</p></div>
  <p style="color:#d5ceff">LEARN • GROW • ACHIEVE</p>
 </div>
 <div class="login-box">
  <div class="eyebrow">SECURE PORTAL ACCESS</div><h2>Welcome back!</h2><p class="muted">Select your role and sign in to continue.</p>
  <div class="roles"><button id="r-student" class="role active" onclick="setRole('student')">🎓 Student</button><button id="r-teacher" class="role" onclick="setRole('teacher')">📘 Teacher</button><button id="r-parent" class="role" onclick="setRole('parent')">👨‍👩‍👧 Parent</button></div>
  <form id="loginForm"><label>Username</label><input id="username" required placeholder="Enter username" autocomplete="username"><label>Password</label><input id="password" type="password" required placeholder="Enter password" autocomplete="current-password"><div id="loginError" class="err"></div><button class="btn" type="submit">Sign in to dashboard →</button></form>
  <div class="notice">Demo accounts<br><b>Student:</b> student / student123<br><b>Teacher:</b> teacher / teacher123<br><b>Parent:</b> parent / parent123</div>
 </div>
</section>

<section id="portal" class="app hidden">
 <aside class="sidebar">
  <div class="side-label">WORKSPACE</div>
  <button class="side-btn active" onclick="showTab('overview',this)">▦ &nbsp; Overview</button>
  <button class="side-btn" onclick="showTab('records',this)" id="recordsNav">▤ &nbsp; Student Records</button>
  <button class="side-btn" onclick="showTab('progress',this)">◷ &nbsp; Academic Progress</button>
  <button class="side-btn" onclick="showTab('announcements',this)">✧ &nbsp; Announcements</button>
  <div class="side-label">ACCOUNT</div>
  <button class="side-btn" onclick="logout()">↪ &nbsp; Sign Out</button>
 </aside>
 <main class="main">
  <div class="page-head"><div><div class="eyebrow" id="roleCaption">ACADEMIC WORKSPACE</div><h1 id="dashTitle">Overview</h1><div class="muted" id="dashSub">Your academic snapshot.</div></div><span class="pill" id="rolePill">STUDENT PORTAL</span></div>
  <div id="overviewTab">
   <div class="metrics" id="metrics"></div>
   <div class="two-col">
    <div class="panel"><div class="panel-head"><h2>Performance snapshot</h2><span class="pill">ACADEMIC</span></div><div id="chart"></div></div>
    <div class="panel"><div class="panel-head"><h2>Campus updates</h2>🔔</div><div class="notice">📚 <b>Academic reminder</b><br>Keep your learning records updated.</div><div class="notice">📅 <b>Attendance matters</b><br>Regular attendance supports progress.</div><div class="notice">🌟 <b>Keep achieving</b><br>Celebrate small improvements every day.</div></div>
   </div>
  </div>
  <div id="recordsTab" class="hidden">
   <div class="panel" id="managePanel">
    <div class="panel-head"><h2>Student management</h2><button class="btn-light" onclick="exportCSV()">⬇ Export CSV</button></div>
    <form id="studentForm">
     <input id="studentId" type="hidden">
     <div class="form-grid">
      <div><label>Full name *</label><input id="name" required></div>
      <div><label>Roll number *</label><input id="roll" required></div>  
      <div><label>Class / Division *</label><input id="studentClass" required></div>
      <div><label>Email</label><input id="email" type="email"></div>
      <div><label>Marks (0–100)</label><input id="marks" type="number" min="0" max="100" value="0" required></div>
      <div><label>Attendance %</label><input id="att" type="number" min="0" max="100" value="100" required></div>
     </div>
     <div style="display:flex;gap:9px;margin-top:16px"><button class="btn" type="submit" id="saveBtn">＋ Add student</button><button class="btn-light" type="button" onclick="resetForm()">Clear form</button></div>
    </form><p id="message" class="muted"></p>
   </div>
   <div class="panel"><div class="panel-head"><h2>Student directory</h2><span class="pill" id="recordCount">0 RECORDS</span></div><input id="search" placeholder="⌕  Search by student name or roll number"><div class="table-wrap"><table><thead><tr><th>STUDENT</th><th>ROLL</th><th>CLASS</th><th>MARKS</th><th>ATTENDANCE</th><th id="actionsHeading">ACTIONS</th></tr></thead><tbody id="rows"></tbody></table></div></div>
  </div>
  <div id="progressTab" class="hidden"><div class="panel"><h2>Academic progress</h2><p class="muted">A summary of academic results and attendance based on available student records.</p><div id="progressContent"></div></div></div>
  <div id="announcementsTab" class="hidden"><div class="panel"><h2>Announcements & reminders</h2><div class="notice">📢 <b>Welcome to EduSphere Pro</b><br>Use your role dashboard to view the relevant academic information.</div><div class="notice">📅 <b>Attendance review</b><br>Check attendance regularly and follow up where needed.</div><div class="notice">⭐ <b>Academic progress</b><br>Teachers can update marks from Student Records.</div><p class="muted">These are sample announcements for the project demo.</p></div></div>
 </main>
</section>
<footer>EduSphere Pro · Student Management System · Academic excellence through technology</footer>

<script>
let role='student',records=[];
const $=id=>document.getElementById(id);
function goHome(){$('landing').classList.remove('hidden');$('login').classList.add('hidden');$('portal').classList.add('hidden');window.scrollTo(0,0)}
function showLogin(r){setRole(r);$('landing').classList.add('hidden');$('portal').classList.add('hidden');$('login').classList.remove('hidden');$('loginError').textContent='';window.scrollTo(0,0)}
function setRole(r){role=r;['student','teacher','parent'].forEach(x=>$('r-'+x).classList.toggle('active',x===r));$('username').placeholder='Enter '+r+' username';$('password').value=''}
async function api(url,opts={}){let res=await fetch(url,opts),data={};try{data=await res.json()}catch(e){}if(!res.ok)throw Error(data.error||'Request failed');return data}
$('loginForm').addEventListener('submit',async e=>{e.preventDefault();$('loginError').textContent='Signing in...';try{await api('/api/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({username:$('username').value.trim(),password:$('password').value,role})});await openPortal()}catch(err){$('loginError').textContent=err.message}});
async function openPortal(){$('login').classList.add('hidden');$('landing').classList.add('hidden');$('portal').classList.remove('hidden');$('roleCaption').textContent=role.toUpperCase()+' WORKSPACE';$('rolePill').textContent=role.toUpperCase()+' PORTAL';$('dashTitle').textContent=({student:'Welcome, Student 🎓',teacher:'Teacher Dashboard 📘',parent:'Parent Dashboard 👨‍👩‍👧'})[role];$('dashSub').textContent=({student:'Your learning journey at a glance.',teacher:'Manage student records and academic performance.',parent:'A snapshot of your child’s academic journey.'})[role];$('recordsNav').classList.toggle('hidden',role!=='teacher');$('managePanel').classList.toggle('hidden',role!=='teacher');$('actionsHeading').classList.toggle('hidden',role!=='teacher');await loadData();showTab('overview',document.querySelector('.side-btn'))}
function logout(){$('loginForm').reset();resetForm();goHome()}
function showTab(tab,button){['overview','records','progress','announcements'].forEach(x=>$(x+'Tab').classList.toggle('hidden',x!==tab));document.querySelectorAll('.side-btn').forEach(b=>b.classList.remove('active'));if(button)button.classList.add('active');if(tab==='records'&&role!=='teacher'){showTab('overview',document.querySelector('.side-btn'));return}if(tab==='progress')renderProgress()}
function safe(v){return String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
async function loadData(){try{records=await api('/api/students')}catch(e){records=[]}renderAll()}
function renderAll(){let count=records.length,avg=count?records.reduce((a,s)=>a+Number(s.marks||0),0)/count:0,att=count?records.reduce((a,s)=>a+Number(s.att||0),0)/count:0;let m;
if(role==='teacher')m=[['👥','Total students',count,'Records in directory'],['📊','Average marks',avg.toFixed(1),'Out of 100'],['◷','Attendance',att.toFixed(1)+'%','Average attendance'],['🏅','High performers',records.filter(s=>Number(s.marks)>=80).length,'Marks 80 and above']];
else if(role==='parent'){let s=records[0];m=[['🎓','Linked student',s?s.name:'Not linked','Demo profile'],['📊','Latest marks',s?s.marks+'/100':'—','Sample record'],['◷','Attendance',s?s.att+'%':'—','Sample record'],['✦','Academic status',s&&Number(s.marks)>=80?'Excellent':'In progress','Based on sample data']]}
else{let s=records[0];m=[['📊','Marks',s?s.marks+'/100':'—','Demo student record'],['◷','Attendance',s?s.att+'%':'—','Demo student record'],['🏅','Performance',s&&Number(s.marks)>=80?'Excellent':'In progress','Sample status'],['✦','Learning goals','Keep going','Every step counts']]}
$('metrics').innerHTML=m.map(x=>'<div class="metric"><span class="m-icon">'+x[0]+'</span><small>'+x[1]+'</small><strong>'+safe(x[2])+'</strong><small>'+x[3]+'</small></div>').join('');
$('chart').innerHTML=[['Academic performance',avg],['Attendance',att],['High performers',count?records.filter(s=>Number(s.marks)>=80).length/count*100:0]].map(x=>'<div class="bar-row"><span>'+x[0]+'</span><div class="bar"><span style="width:'+Math.max(0,Math.min(100,Number(x[1])||0))+'%"></span></div><b>'+Number(x[1]).toFixed(0)+'%</b></div>').join('')+'<p class="muted">Visuals are calculated from available demo records.</p>';
renderRows();renderProgress()}
function renderRows(){let term=$('search').value.toLowerCase();let list=records.filter(s=>(s.name+' '+s.roll+' '+s.studentClass).toLowerCase().includes(term));$('recordCount').textContent=list.length+' RECORDS';$('rows').innerHTML=list.map(s=>'<tr><td><span class="avatar">'+safe((s.name||'?').charAt(0).toUpperCase())+'</span>'+safe(s.name)+'</td><td>'+safe(s.roll)+'</td><td>'+safe(s.studentClass)+'</td><td><span class="tag">'+safe(s.marks)+'/100</span></td><td>'+safe(s.att)+'%</td><td class="'+(role==='teacher'?'':'hidden')+'">'+(role==='teacher'?'<button class="btn-light" data-edit="'+safe(s.id)+'">Edit</button> <button class="btn-danger" data-del="'+safe(s.id)+'">Delete</button>':'')+'</td></tr>').join('')||'<tr><td colspan="6">No student records found.</td></tr>'}
function renderProgress(){$('progressContent').innerHTML=records.length?records.map(s=>'<div class="panel"><div class="panel-head"><h3>'+safe(s.name)+' · '+safe(s.studentClass)+'</h3><span class="pill">Roll '+safe(s.roll)+'</span></div><p>Marks: <b>'+safe(s.marks)+'/100</b></p><div class="bar"><span style="width:'+Math.max(0,Math.min(100,Number(s.marks)||0))+'%"></span></div><p>Attendance: <b>'+safe(s.att)+'%</b></p><div class="bar"><span style="width:'+Math.max(0,Math.min(100,Number(s.att)||0))+'%;background:linear-gradient(90deg,#49b58a,#83d9b4)"></span></div></div>').join(''):'<p class="muted">No academic records are available yet.</p>'}
$('search').addEventListener('input',renderRows);
function resetForm(){$('studentForm').reset();$('studentId').value='';$('marks').value=0;$('att').value=100;$('saveBtn').textContent='＋ Add student'}
$('studentForm').addEventListener('submit',async e=>{e.preventDefault();let id=$('studentId').value;let body=new URLSearchParams({id,name:$('name').value.trim(),roll:$('roll').value.trim(),studentClass:$('studentClass').value.trim(),email:$('email').value.trim(),marks:$('marks').value,att:$('att').value});try{await api('/api/students',{method:id?'PUT':'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body});resetForm();$('message').textContent='✓ Student record saved successfully.';await loadData()}catch(err){$('message').textContent=err.message}});
$('rows').addEventListener('click',async e=>{let ed=e.target.closest('[data-edit]'),del=e.target.closest('[data-del]');if(ed){let s=records.find(x=>x.id===ed.dataset.edit);if(!s)return;[['studentId','id'],['name','name'],['roll','roll'],['studentClass','studentClass'],['email','email'],['marks','marks'],['att','att']].forEach(([a,b])=>$(a).value=s[b]||'');$('saveBtn').textContent='Save changes';$('managePanel').scrollIntoView({behavior:'smooth'})}if(del&&confirm('Delete this student record?')){try{await api('/api/students?id='+encodeURIComponent(del.dataset.del),{method:'DELETE'});await loadData()}catch(err){alert(err.message)}}});
function exportCSV(){let lines=[['Name','Roll','Class','Email','Marks','Attendance'],...records.map(s=>[s.name,s.roll,s.studentClass,s.email,s.marks,s.att])];let csv=lines.map(row=>row.map(v=>'"'+String(v??'').replace(/"/g,'""')+'"').join(',')).join('\\r\\n');let a=document.createElement('a');a.href=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8;'}));a.download='EduSphere_Student_Report.csv';a.click();URL.revokeObjectURL(a.href)}
</script>
</body>
</html>
""";
	}
       
}
