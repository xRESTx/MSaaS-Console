import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  Clipboard,
  Code2,
  FileJson,
  Globe2,
  KeyRound,
  Layers3,
  LogOut,
  Moon,
  Play,
  Plus,
  RefreshCw,
  RotateCw,
  Search,
  Send,
  Server,
  ShieldCheck,
  Sun,
  Trash2,
  Upload,
  Zap
} from "lucide-react";
import "./styles.css";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";

type Lang = "ru" | "en";
type Theme = "light" | "dark";

type User = {
  id: string;
  email: string;
};

type Project = {
  id: string;
  name: string;
  description: string;
};

type SpecVersion = {
  id: string;
  projectId: string;
  versionNumber: number;
  name: string;
  status: "VALID" | "INVALID";
  validationErrors: string[];
  routeCount: number;
};

type MockInstance = {
  id: string;
  projectId: string;
  specVersionId: string;
  publicUrl: string;
  mode: "STATELESS" | "STATEFUL";
  status: string;
  routeCount: number;
};

type RequestLog = {
  id: string;
  method: string;
  path: string;
  queryString: string | null;
  responseStatus: number;
  matched: boolean;
  error: string | null;
  latencyMs: number;
  receivedAt: string;
};

type AuthResponse = {
  token: string;
  user: User;
};

type DeleteTarget =
  | { kind: "project"; id: string; name: string }
  | { kind: "spec"; id: string; name: string }
  | { kind: "instance"; id: string; name: string };

const sampleSpec = `openapi: 3.0.3
info:
  title: Demo Orders API
  version: 1.0.0
paths:
  /orders:
    get:
      responses:
        "200":
          description: Orders list
          content:
            application/json:
              schema:
                type: array
                items:
                  type: object
                  properties:
                    id:
                      type: string
                    title:
                      type: string
                    paid:
                      type: boolean
    post:
      responses:
        "201":
          description: Created order
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: string
                  title:
                    type: string
                  paid:
                    type: boolean
  /orders/{id}:
    get:
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        "200":
          description: One order
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: string
                  title:
                    type: string
                  paid:
                    type: boolean
    put:
      responses:
        "200":
          description: Updated order
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: string
                  title:
                    type: string
                  paid:
                    type: boolean
    delete:
      responses:
        "204":
          description: Deleted order
`;

const text = {
  ru: {
    access: "Вход",
    account: "Учетная запись",
    apiContract: "API-контракт",
    callMock: "Вызов mock",
    clear: "Очистить",
    contract: "Контракт",
    copied: "Ссылка скопирована",
    createProject: "Создать проект",
    dark: "Темная",
    description: "Описание",
    diagnostics: "Диагностика",
    email: "Email",
    features: ["Приватные проекты", "OpenAPI validation", "Warm runtime slots"],
    file: "Файл OpenAPI",
    instance: "Инстанс",
    instances: "Инстансы",
    language: "Язык",
    light: "Светлая",
    login: "Войти",
    logout: "Выйти",
    logs: "Журналы",
    mockBody: "Тело запроса",
    mockPath: "Путь",
    name: "Название",
    noInstances: "Опубликуй валидную версию спецификации, чтобы получить mock URL.",
    noLogs: "Пока нет запросов к выбранному инстансу.",
    noProject: "Создай или выбери проект",
    password: "Пароль",
    pipeline: "Путь публикации",
    projectCreated: "Проект создан",
    projects: "Проекты",
    publish: "Опубликовать",
    refresh: "Обновить",
    register: "Зарегистрироваться",
    resetState: "Сбросить state",
    response: "Ответ",
    rotateToken: "Новая ссылка",
    running: "Активные",
    send: "Отправить",
    signedIn: "Выполнен вход",
    specName: "Имя спецификации",
    specUploadedInvalid: "Спецификация загружена с ошибками",
    specUploadedValid: "Спецификация загружена и проверена",
    specs: "Версии",
    subtitle: "Консоль быстрого создания и диагностики mock API по OpenAPI.",
    theme: "Тема",
    tokenRotated: "Публичная ссылка пересоздана",
    uploadSpec: "Загрузить спецификацию",
    workspace: "Рабочее пространство",
    stateReset: "Состояние runtime сброшено",
    published: "Mock-инстанс опубликован",
    routes: "маршрутов",
    valid: "валидных",
    invalid: "ошибка",
    matched: "совпало",
    unmatched: "нет совпадения",
    publicUrl: "Публичная ссылка",
    latestActivity: "Последняя активность",
    quickStart: "Создай проект, загрузи контракт и получи URL для тестового клиента.",
    delete: "Удалить",
    deleteProject: "Удалить проект",
    deleteSpec: "Удалить версию",
    deleteInstance: "Удалить инстанс",
    deleteWarning: "Это действие нельзя отменить.",
    confirmProjectName: "Введите название проекта для подтверждения",
    cancel: "Отмена",
    confirmDelete: "Подтвердить удаление",
    projectDeleted: "Проект удалён",
    specDeleted: "Версия спецификации удалена",
    instanceDeleted: "Инстанс удалён",
    deleteProjectDetail: "Будут удалены все версии спецификаций, опубликованные инстансы и журналы проекта.",
    deleteSpecDetail: "Будут удалены эта версия, связанные инстансы и их журналы.",
    deleteInstanceDetail: "Публичная ссылка перестанет работать, журналы этого инстанса будут удалены."
  },
  en: {
    access: "Access",
    account: "Account",
    apiContract: "API contract",
    callMock: "Call mock",
    clear: "Clear",
    contract: "Contract",
    copied: "Link copied",
    createProject: "Create project",
    dark: "Dark",
    description: "Description",
    diagnostics: "Diagnostics",
    email: "Email",
    features: ["Private projects", "OpenAPI validation", "Warm runtime slots"],
    file: "OpenAPI file",
    instance: "Instance",
    instances: "Instances",
    language: "Language",
    light: "Light",
    login: "Login",
    logout: "Logout",
    logs: "Logs",
    mockBody: "Request body",
    mockPath: "Path",
    name: "Name",
    noInstances: "Publish a valid specification version to receive a mock URL.",
    noLogs: "No requests for the selected instance yet.",
    noProject: "Create or select a project",
    password: "Password",
    pipeline: "Publication path",
    projectCreated: "Project created",
    projects: "Projects",
    publish: "Publish",
    refresh: "Refresh",
    register: "Register",
    resetState: "Reset state",
    response: "Response",
    rotateToken: "Rotate link",
    running: "Running",
    send: "Send",
    signedIn: "Signed in",
    specName: "Specification name",
    specUploadedInvalid: "Specification uploaded with validation errors",
    specUploadedValid: "Specification uploaded and validated",
    specs: "Versions",
    subtitle: "Console for publishing and diagnosing OpenAPI-backed mock APIs.",
    theme: "Theme",
    tokenRotated: "Public link rotated",
    uploadSpec: "Upload specification",
    workspace: "Workspace",
    stateReset: "Runtime state reset",
    published: "Mock instance published",
    routes: "routes",
    valid: "valid",
    invalid: "invalid",
    matched: "matched",
    unmatched: "unmatched",
    publicUrl: "Public URL",
    latestActivity: "Latest activity",
    quickStart: "Create a project, upload a contract, and get a URL for your test client.",
    delete: "Delete",
    deleteProject: "Delete project",
    deleteSpec: "Delete version",
    deleteInstance: "Delete instance",
    deleteWarning: "This action cannot be undone.",
    confirmProjectName: "Type the project name to confirm",
    cancel: "Cancel",
    confirmDelete: "Confirm delete",
    projectDeleted: "Project deleted",
    specDeleted: "Specification version deleted",
    instanceDeleted: "Instance deleted",
    deleteProjectDetail: "All specification versions, published instances, and request logs in this project will be deleted.",
    deleteSpecDetail: "This version, related instances, and their logs will be deleted.",
    deleteInstanceDetail: "The public URL will stop working and this instance's logs will be deleted."
  }
} satisfies Record<Lang, Record<string, string | string[]>>;

function App() {
  const [theme, setTheme] = useState<Theme>(() => (localStorage.getItem("msaas.theme") as Theme) || "light");
  const [lang, setLang] = useState<Lang>(() => (localStorage.getItem("msaas.lang") as Lang) || "ru");
  const [token, setToken] = useState(() => localStorage.getItem("msaas.token") ?? "");
  const [user, setUser] = useState<User | null>(null);
  const [email, setEmail] = useState("demo@example.com");
  const [password, setPassword] = useState("password");
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [projectName, setProjectName] = useState("Demo API");
  const [projectDescription, setProjectDescription] = useState("OpenAPI-backed mock project");
  const [specName, setSpecName] = useState("orders-openapi.yaml");
  const [specSource, setSpecSource] = useState(sampleSpec);
  const [specVersions, setSpecVersions] = useState<SpecVersion[]>([]);
  const [instances, setInstances] = useState<MockInstance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState("");
  const [publishMode, setPublishMode] = useState<"STATELESS" | "STATEFUL">("STATEFUL");
  const [mockMethod, setMockMethod] = useState("GET");
  const [mockPath, setMockPath] = useState("/orders");
  const [mockBody, setMockBody] = useState('{"title":"Test order","paid":false}');
  const [mockResponse, setMockResponse] = useState("");
  const [logs, setLogs] = useState<RequestLog[]>([]);
  const [message, setMessage] = useState("");
  const [projectFilter, setProjectFilter] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  const [deleteConfirmName, setDeleteConfirmName] = useState("");

  const t = text[lang];
  const selectedProject = projects.find((project) => project.id === selectedProjectId) ?? null;
  const selectedInstance = instances.find((instance) => instance.id === selectedInstanceId) ?? null;
  const filteredProjects = projects.filter((project) =>
    `${project.name} ${project.description}`.toLowerCase().includes(projectFilter.toLowerCase())
  );
  const runningInstances = instances.filter((instance) => instance.status === "RUNNING").length;
  const validSpecs = specVersions.filter((version) => version.status === "VALID").length;

  const authHeaders = useMemo(() => ({
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`
  }), [token]);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("msaas.theme", theme);
  }, [theme]);

  useEffect(() => {
    document.documentElement.lang = lang;
    localStorage.setItem("msaas.lang", lang);
  }, [lang]);

  useEffect(() => {
    if (token) {
      void refreshProjects();
    }
  }, [token]);

  useEffect(() => {
    if (selectedProjectId) {
      void refreshProjectDetails(selectedProjectId);
    }
  }, [selectedProjectId]);

  async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(`${API_BASE}${path}`, init);
    const textBody = await response.text();
    const body = textBody ? JSON.parse(textBody) : null;
    if (!response.ok) {
      throw new Error(body?.error ?? body?.details?.join(", ") ?? response.statusText);
    }
    return body as T;
  }

  async function auth(path: "/api/auth/login" | "/api/auth/register") {
    try {
      const data = await api<AuthResponse>(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password })
      });
      localStorage.setItem("msaas.token", data.token);
      setToken(data.token);
      setUser(data.user);
      setMessage(`${t.signedIn}: ${data.user.email}`);
    } catch (error) {
      showError(error);
    }
  }

  async function refreshProjects() {
    try {
      const data = await api<Project[]>("/api/projects", { headers: authHeaders });
      setProjects(data);
      if (!selectedProjectId && data.length > 0) {
        setSelectedProjectId(data[0].id);
      }
    } catch (error) {
      showError(error);
    }
  }

  async function createProject() {
    try {
      const project = await api<Project>("/api/projects", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ name: projectName, description: projectDescription })
      });
      setProjects((current) => [project, ...current]);
      setSelectedProjectId(project.id);
      setMessage(String(t.projectCreated));
    } catch (error) {
      showError(error);
    }
  }

  async function refreshProjectDetails(projectId: string) {
    try {
      const [versions, projectInstances] = await Promise.all([
        api<SpecVersion[]>(`/api/projects/${projectId}/spec-versions`, { headers: authHeaders }),
        api<MockInstance[]>(`/api/projects/${projectId}/instances`, { headers: authHeaders })
      ]);
      setSpecVersions(versions);
      setInstances(projectInstances);
      setSelectedInstanceId((current) =>
        projectInstances.some((instance) => instance.id === current) ? current : projectInstances[0]?.id ?? ""
      );
      setLogs([]);
    } catch (error) {
      showError(error);
    }
  }

  async function uploadSpec() {
    if (!selectedProjectId) {
      setMessage(String(t.noProject));
      return;
    }
    try {
      const version = await api<SpecVersion>(`/api/projects/${selectedProjectId}/spec-versions`, {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ name: specName, source: specSource })
      });
      setSpecVersions((current) => [version, ...current]);
      setMessage(version.status === "VALID" ? String(t.specUploadedValid) : String(t.specUploadedInvalid));
    } catch (error) {
      showError(error);
    }
  }

  async function publish(versionId: string) {
    try {
      const instance = await api<MockInstance>(`/api/spec-versions/${versionId}/publish`, {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ mode: publishMode })
      });
      setInstances((current) => [instance, ...current]);
      setSelectedInstanceId(instance.id);
      setMessage(String(t.published));
    } catch (error) {
      showError(error);
    }
  }

  async function rotateToken() {
    if (!selectedInstance) return;
    try {
      const instance = await api<MockInstance>(`/api/instances/${selectedInstance.id}/rotate-token`, {
        method: "POST",
        headers: authHeaders
      });
      setInstances((current) => current.map((item) => (item.id === instance.id ? instance : item)));
      setMessage(String(t.tokenRotated));
    } catch (error) {
      showError(error);
    }
  }

  async function deleteProject(project: Project) {
    try {
      await api<void>(`/api/projects/${project.id}`, {
        method: "DELETE",
        headers: authHeaders,
        body: JSON.stringify({ confirmName: deleteConfirmName })
      });
      setProjects((current) => current.filter((item) => item.id !== project.id));
      if (selectedProjectId === project.id) {
        setSelectedProjectId("");
        setSpecVersions([]);
        setInstances([]);
        setSelectedInstanceId("");
        setLogs([]);
      }
      closeDeleteDialog();
      setMessage(String(t.projectDeleted));
    } catch (error) {
      showError(error);
    }
  }

  async function deleteSpecVersion(version: SpecVersion) {
    try {
      await api<void>(`/api/spec-versions/${version.id}`, {
        method: "DELETE",
        headers: authHeaders
      });
      setSpecVersions((current) => current.filter((item) => item.id !== version.id));
      if (selectedProjectId) {
        await refreshProjectDetails(selectedProjectId);
      }
      closeDeleteDialog();
      setMessage(String(t.specDeleted));
    } catch (error) {
      showError(error);
    }
  }

  async function deleteInstance(instance: MockInstance) {
    try {
      await api<void>(`/api/instances/${instance.id}`, {
        method: "DELETE",
        headers: authHeaders
      });
      setInstances((current) => current.filter((item) => item.id !== instance.id));
      if (selectedInstanceId === instance.id) {
        setSelectedInstanceId("");
        setLogs([]);
      }
      closeDeleteDialog();
      setMessage(String(t.instanceDeleted));
    } catch (error) {
      showError(error);
    }
  }

  async function resetState() {
    if (!selectedInstance) return;
    try {
      const instance = await api<MockInstance>(`/api/instances/${selectedInstance.id}/reset-state`, {
        method: "POST",
        headers: authHeaders
      });
      setInstances((current) => current.map((item) => (item.id === instance.id ? instance : item)));
      setMessage(String(t.stateReset));
    } catch (error) {
      showError(error);
    }
  }

  async function refreshLogs() {
    if (!selectedInstance) return;
    try {
      setLogs(await api<RequestLog[]>(`/api/instances/${selectedInstance.id}/logs`, { headers: authHeaders }));
    } catch (error) {
      showError(error);
    }
  }

  async function callMock() {
    if (!selectedInstance) return;
    try {
      const response = await fetch(`${selectedInstance.publicUrl}${mockPath}`, {
        method: mockMethod,
        headers: mockMethod === "GET" || mockMethod === "DELETE" ? undefined : { "Content-Type": "application/json" },
        body: mockMethod === "GET" || mockMethod === "DELETE" ? undefined : mockBody
      });
      const responseText = await response.text();
      setMockResponse(`${response.status} ${response.statusText}\n${responseText}`);
      await refreshLogs();
    } catch (error) {
      showError(error);
    }
  }

  async function copyPublicUrl() {
    if (!selectedInstance) return;
    await navigator.clipboard.writeText(selectedInstance.publicUrl);
    setMessage(String(t.copied));
  }

  function handleFile(file: File | null) {
    if (!file) return;
    setSpecName(file.name);
    file.text().then(setSpecSource).catch(showError);
  }

  function openDeleteDialog(target: DeleteTarget) {
    setDeleteTarget(target);
    setDeleteConfirmName("");
  }

  function closeDeleteDialog() {
    setDeleteTarget(null);
    setDeleteConfirmName("");
  }

  function confirmDeletion() {
    if (!deleteTarget) return;
    if (deleteTarget.kind === "project") {
      const project = projects.find((item) => item.id === deleteTarget.id);
      if (project) void deleteProject(project);
      return;
    }
    if (deleteTarget.kind === "spec") {
      const version = specVersions.find((item) => item.id === deleteTarget.id);
      if (version) void deleteSpecVersion(version);
      return;
    }
    const instance = instances.find((item) => item.id === deleteTarget.id);
    if (instance) void deleteInstance(instance);
  }

  function logout() {
    localStorage.removeItem("msaas.token");
    setToken("");
    setUser(null);
    setProjects([]);
    setSelectedProjectId("");
    setSpecVersions([]);
    setInstances([]);
    setLogs([]);
  }

  function showError(error: unknown) {
    setMessage(error instanceof Error ? error.message : String(error));
  }

  function deleteTitle(target: DeleteTarget) {
    if (target.kind === "project") return String(t.deleteProject);
    if (target.kind === "spec") return String(t.deleteSpec);
    return String(t.deleteInstance);
  }

  function deleteDetail(target: DeleteTarget) {
    if (target.kind === "project") return String(t.deleteProjectDetail);
    if (target.kind === "spec") return String(t.deleteSpecDetail);
    return String(t.deleteInstanceDetail);
  }

  const shellControls = (
    <div className="shell-controls" aria-label="preferences">
      <button className="icon-button" title={String(t.theme)} onClick={() => setTheme(theme === "light" ? "dark" : "light")}>
        {theme === "light" ? <Moon size={18} /> : <Sun size={18} />}
      </button>
      <button className="segmented-button" onClick={() => setLang(lang === "ru" ? "en" : "ru")}>
        <Globe2 size={16} />
        {lang.toUpperCase()}
      </button>
      {token && (
        <button className="ghost-button" onClick={logout}>
          <LogOut size={17} />
          {String(t.logout)}
        </button>
      )}
    </div>
  );

  if (!token) {
    return (
      <main className="auth-shell">
        <header className="auth-topbar">
          <Brand />
          {shellControls}
        </header>
        <section className="auth-grid">
          <div className="auth-story">
            <p className="eyebrow">MSaaS Console</p>
            <h1>{String(t.subtitle)}</h1>
            <p className="lead">{String(t.quickStart)}</p>
            <div className="flow-visual" aria-label={String(t.pipeline)}>
              <FlowStep icon={<FileJson size={20} />} label={String(t.contract)} value="OpenAPI" />
              <ArrowRight size={18} />
              <FlowStep icon={<Server size={20} />} label={String(t.instance)} value="Warm slot" />
              <ArrowRight size={18} />
              <FlowStep icon={<Activity size={20} />} label={String(t.diagnostics)} value="Logs" />
            </div>
            <div className="feature-strip">
              {(t.features as string[]).map((feature) => (
                <span key={feature}>
                  <ShieldCheck size={15} />
                  {feature}
                </span>
              ))}
            </div>
          </div>
          <section className="auth-card" aria-label={String(t.access)}>
            <div>
              <p className="eyebrow">{String(t.account)}</p>
              <h2>{String(t.access)}</h2>
            </div>
            <label>{String(t.email)}<input value={email} onChange={(event) => setEmail(event.target.value)} /></label>
            <label>{String(t.password)}<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
            <div className="button-row">
              <button className="primary-button" onClick={() => auth("/api/auth/login")}>
                <KeyRound size={17} />
                {String(t.login)}
              </button>
              <button className="soft-button" onClick={() => auth("/api/auth/register")}>
                <Plus size={17} />
                {String(t.register)}
              </button>
            </div>
            {message && <p className="notice compact">{message}</p>}
          </section>
        </section>
      </main>
    );
  }

  return (
    <main className="console-shell">
      <aside className="sidebar">
        <Brand />
        <div className="sidebar-section">
          <div className="section-heading">
            <span>{String(t.projects)}</span>
            <button className="icon-button small" title={String(t.refresh)} onClick={refreshProjects}>
              <RefreshCw size={15} />
            </button>
          </div>
          <div className="search-box">
            <Search size={16} />
            <input value={projectFilter} onChange={(event) => setProjectFilter(event.target.value)} placeholder={String(t.projects)} />
          </div>
          <div className="project-list">
            {filteredProjects.map((project) => (
              <button
                key={project.id}
                className={cx("project-button", project.id === selectedProjectId && "active")}
                onClick={() => setSelectedProjectId(project.id)}
              >
                <span className="project-avatar">{project.name.slice(0, 2).toUpperCase()}</span>
                <span>
                  <strong>{project.name}</strong>
                  <small>{project.description || "OpenAPI"}</small>
                </span>
              </button>
            ))}
          </div>
        </div>
        <div className="sidebar-section create-box">
          <label>{String(t.name)}<input value={projectName} onChange={(event) => setProjectName(event.target.value)} /></label>
          <label>{String(t.description)}<input value={projectDescription} onChange={(event) => setProjectDescription(event.target.value)} /></label>
          <button className="primary-button full" onClick={createProject}>
            <Plus size={17} />
            {String(t.createProject)}
          </button>
        </div>
      </aside>

      <section className="main-column">
        <header className="console-topbar">
          <div>
            <p className="eyebrow">{String(t.workspace)}</p>
            <h1>{selectedProject?.name ?? String(t.noProject)}</h1>
          </div>
          <div className="topbar-actions">
            {selectedProject && (
              <button
                className="danger-button"
                onClick={() => openDeleteDialog({ kind: "project", id: selectedProject.id, name: selectedProject.name })}
              >
                <Trash2 size={17} />
                {String(t.deleteProject)}
              </button>
            )}
            {shellControls}
          </div>
        </header>

        {message && <div className="notice">{message}</div>}

        <section className="stats-grid">
          <Metric icon={<Layers3 size={19} />} label={String(t.projects)} value={projects.length} />
          <Metric icon={<CheckCircle2 size={19} />} label={`${String(t.specs)} ${String(t.valid)}`} value={validSpecs} />
          <Metric icon={<Zap size={19} />} label={String(t.running)} value={runningInstances} />
          <Metric icon={<Activity size={19} />} label={String(t.logs)} value={logs.length} />
        </section>

        <section className="work-grid">
          <article className="surface spec-surface">
            <div className="surface-heading">
              <div>
                <p className="eyebrow">{String(t.apiContract)}</p>
                <h2>{String(t.uploadSpec)}</h2>
              </div>
              <button className="primary-button" onClick={uploadSpec} disabled={!selectedProjectId}>
                <Upload size={17} />
                {String(t.uploadSpec)}
              </button>
            </div>
            <div className="form-grid two">
              <label>{String(t.specName)}<input value={specName} onChange={(event) => setSpecName(event.target.value)} /></label>
              <label>{String(t.file)}<input type="file" accept=".yaml,.yml,.json" onChange={(event) => handleFile(event.target.files?.[0] ?? null)} /></label>
            </div>
            <textarea className="code-editor" value={specSource} onChange={(event) => setSpecSource(event.target.value)} spellCheck={false} />
          </article>

          <article className="surface">
            <div className="surface-heading">
              <div>
                <p className="eyebrow">{String(t.specs)}</p>
                <h2>{String(t.pipeline)}</h2>
              </div>
              <select className="compact-select" value={publishMode} onChange={(event) => setPublishMode(event.target.value as "STATELESS" | "STATEFUL")}>
                <option value="STATEFUL">STATEFUL</option>
                <option value="STATELESS">STATELESS</option>
              </select>
            </div>
            <div className="timeline-list">
              {specVersions.map((version) => (
                <div className="version-card" key={version.id}>
                  <div className="version-meta">
                    <span className={cx("status-pill", version.status === "VALID" ? "success" : "danger")}>
                      {version.status === "VALID" ? String(t.valid) : String(t.invalid)}
                    </span>
                    <strong>v{version.versionNumber}: {version.name}</strong>
                    <small>{version.routeCount} {String(t.routes)}</small>
                    {version.validationErrors.length > 0 && <p className="validation-text">{version.validationErrors.join("; ")}</p>}
                  </div>
                  <div className="card-actions">
                    <button className="soft-button" disabled={version.status !== "VALID"} onClick={() => publish(version.id)}>
                      <Play size={16} />
                      {String(t.publish)}
                    </button>
                    <button
                      className="icon-button danger-icon"
                      title={String(t.deleteSpec)}
                      onClick={() => openDeleteDialog({ kind: "spec", id: version.id, name: version.name })}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}
              {specVersions.length === 0 && <EmptyState icon={<FileJson size={28} />} text={String(t.noProject)} />}
            </div>
          </article>
        </section>

        <section className="runtime-grid">
          <article className="surface runtime-panel">
            <div className="surface-heading">
              <div>
                <p className="eyebrow">{String(t.instances)}</p>
                <h2>{String(t.publicUrl)}</h2>
              </div>
              <button className="soft-button" onClick={refreshLogs} disabled={!selectedInstance}>
                <RefreshCw size={16} />
                {String(t.refresh)}
              </button>
            </div>

            <div className="instance-list">
              {instances.map((instance) => (
                <button
                  className={cx("instance-card", instance.id === selectedInstanceId && "active")}
                  key={instance.id}
                  onClick={() => setSelectedInstanceId(instance.id)}
                >
                  <span className="status-dot" />
                  <span>
                    <strong>{instance.status} · {instance.mode}</strong>
                    <small>{instance.routeCount} {String(t.routes)}</small>
                  </span>
                  <code>{instance.publicUrl}</code>
                </button>
              ))}
              {instances.length === 0 && <EmptyState icon={<Server size={28} />} text={String(t.noInstances)} />}
            </div>

            {selectedInstance && (
              <div className="url-actions">
                <input readOnly value={selectedInstance.publicUrl} />
                <button className="icon-button" title={String(t.copied)} onClick={copyPublicUrl}><Clipboard size={17} /></button>
                <button className="soft-button" onClick={resetState}><RotateCw size={16} />{String(t.resetState)}</button>
                <button className="soft-button" onClick={rotateToken}><RefreshCw size={16} />{String(t.rotateToken)}</button>
                <button
                  className="danger-button"
                  onClick={() => openDeleteDialog({ kind: "instance", id: selectedInstance.id, name: selectedInstance.publicUrl })}
                >
                  <Trash2 size={16} />
                  {String(t.deleteInstance)}
                </button>
              </div>
            )}
          </article>

          <article className="surface">
            <div className="surface-heading">
              <div>
                <p className="eyebrow">{String(t.callMock)}</p>
                <h2>{String(t.diagnostics)}</h2>
              </div>
            </div>
            <div className="request-line">
              <select value={mockMethod} onChange={(event) => setMockMethod(event.target.value)}>
                <option>GET</option>
                <option>POST</option>
                <option>PUT</option>
                <option>DELETE</option>
              </select>
              <input value={mockPath} onChange={(event) => setMockPath(event.target.value)} aria-label={String(t.mockPath)} />
              <button className="primary-button" onClick={callMock} disabled={!selectedInstance}>
                <Send size={16} />
                {String(t.send)}
              </button>
            </div>
            <label>{String(t.mockBody)}<textarea className="body-editor" value={mockBody} onChange={(event) => setMockBody(event.target.value)} /></label>
            <div className="response-box">
              <div className="response-heading">
                <span>{String(t.response)}</span>
                <button className="ghost-button tiny" onClick={() => setMockResponse("")}>{String(t.clear)}</button>
              </div>
              <pre>{mockResponse || "—"}</pre>
            </div>
          </article>

          <article className="surface logs-panel">
            <div className="surface-heading">
              <div>
                <p className="eyebrow">{String(t.latestActivity)}</p>
                <h2>{String(t.logs)}</h2>
              </div>
            </div>
            <div className="log-table">
              {logs.map((log) => (
                <div className="log-row" key={log.id}>
                  <span className={cx("method-badge", log.method.toLowerCase())}>{log.method}</span>
                  <strong>{log.path}</strong>
                  <span>{log.responseStatus}</span>
                  <span>{log.latencyMs}ms</span>
                  <span className={cx("status-pill", log.matched ? "success" : "danger")}>
                    {log.matched ? String(t.matched) : String(t.unmatched)}
                  </span>
                </div>
              ))}
              {logs.length === 0 && <EmptyState icon={<Code2 size={28} />} text={String(t.noLogs)} />}
            </div>
          </article>
        </section>
      </section>
      {deleteTarget && (
        <div className="modal-backdrop" role="presentation">
          <section className="confirm-dialog" role="dialog" aria-modal="true" aria-label={deleteTitle(deleteTarget)}>
            <div className="dialog-icon">
              <AlertTriangle size={24} />
            </div>
            <div>
              <p className="eyebrow">{String(t.deleteWarning)}</p>
              <h2>{deleteTitle(deleteTarget)}</h2>
            </div>
            <p className="dialog-copy">{deleteDetail(deleteTarget)}</p>
            <code className="delete-target-name">{deleteTarget.name}</code>
            {deleteTarget.kind === "project" && (
              <label>
                {String(t.confirmProjectName)}
                <input value={deleteConfirmName} onChange={(event) => setDeleteConfirmName(event.target.value)} autoFocus />
              </label>
            )}
            <div className="dialog-actions">
              <button className="soft-button" onClick={closeDeleteDialog}>{String(t.cancel)}</button>
              <button
                className="danger-button"
                disabled={deleteTarget.kind === "project" && deleteConfirmName !== deleteTarget.name}
                onClick={confirmDeletion}
              >
                <Trash2 size={17} />
                {String(t.confirmDelete)}
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}

function Brand() {
  return (
    <div className="brand">
      <span className="brand-mark"><Server size={20} /></span>
      <span>
        <strong>MSaaS</strong>
        <small>Mock Server as a Service</small>
      </span>
    </div>
  );
}

function FlowStep({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flow-step">
      {icon}
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function Metric({ icon, label, value }: { icon: React.ReactNode; label: string; value: number }) {
  return (
    <article className="metric-card">
      <span>{icon}</span>
      <div>
        <strong>{value}</strong>
        <small>{label}</small>
      </div>
    </article>
  );
}

function EmptyState({ icon, text }: { icon: React.ReactNode; text: string }) {
  return (
    <div className="empty-state">
      {icon}
      <span>{text}</span>
    </div>
  );
}

function cx(...values: Array<string | false | null | undefined>) {
  return values.filter(Boolean).join(" ");
}

createRoot(document.getElementById("root")!).render(<App />);
