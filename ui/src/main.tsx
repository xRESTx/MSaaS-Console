import React, { Suspense, useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  CheckCircle2,
  FileJson,
  Globe2,
  KeyRound,
  Layers3,
  Loader2,
  Moon,
  Plus,
  Server,
  ShieldCheck,
  Sun,
  Users,
  Zap
} from "lucide-react";
import "./styles.css";

const ConsoleApp = React.lazy(() => import("./ConsoleApp"));
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";

type Lang = "ru" | "en";
type Theme = "light" | "dark";
type AuthStep = "identifier" | "password" | "register";
type PublicView = "landing" | "auth";

type AuthResponse = {
  token: string;
  user: {
    id: string;
    email: string;
    username: string;
    systemRole: "USER" | "ADMIN";
    disabled: boolean;
  };
};

type AuthLookup = {
  exists: boolean;
  username?: string;
};

const publicText = {
  ru: {
    access: "Доступ",
    account: "Аккаунт",
    accountFound: "Подтверждение входа",
    accountNew: "Регистрация аккаунта",
    back: "Назад",
    continue: "Продолжить",
    diagnostics: "Диагностика",
    email: "Email",
    emailOrUsername: "Email или никнейм",
    language: "Язык",
    login: "Войти",
    password: "Пароль",
    register: "Зарегистрироваться",
    signedIn: "Вход выполнен",
    theme: "Тема",
    username: "Никнейм",
    passwordHint: "Аккаунт найден. Введите пароль, чтобы открыть консоль.",
    registerHint: "Аккаунт с таким идентификатором не найден. Заполните данные для регистрации.",
    subtitle: "Консоль для приватных OpenAPI-проектов, mock-ссылок, сценариев ответов и логов.",
    authTitle: "Вход в консоль управления mock API",
    authCopy: "Используйте email или никнейм. Если аккаунт уже существует, система запросит пароль; если нет, откроет регистрацию с тем же идентификатором.",
    authEntryTitle: "Вход или регистрация",
    authFlow: ["OpenAPI-контракты", "Приватные проекты", "Runtime и сценарии", "Журналы и аудит"],
    authBenefits: ["Ролевой доступ к проектам", "Управление спецификациями и instance", "История запросов и действий"],
    landingNavFeatures: "Возможности",
    landingNavWorkflow: "Как работает",
    landingNavSecurity: "Доступ",
    landingHeroBadge: "Платформа управления mock API по OpenAPI-контрактам",
    heroTitle: "Мок-сервисы за один клик",
    heroCopy: "MSaaS преобразует OpenAPI-спецификации в управляемые mock-сервисы с приватными проектами, сценариями ответов, профилями окружений и журналом запросов. Команды могут проверять интеграции до готовности основного backend-сервиса.",
    landingPrimaryCta: "Создать проект",
    landingSecondaryCta: "Открыть консоль",
    landingHeroProofs: ["Приватные проекты", "Публикация по защищённой ссылке", "Аудит и журналы запросов"],
    landingAudience: ["Разработка", "QA", "Интеграции", "Демонстрационные стенды"],
    landingProofLabel: "Ключевые преимущества",
    landingMetrics: [
      { value: "OpenAPI", label: "контракт становится основой управляемого mock-сервиса" },
      { value: "1 URL", label: "для клиентских приложений, автотестов и интеграционных проверок" },
      { value: "Profiles", label: "отдельные режимы поведения для разработки, QA и демонстраций" },
      { value: "Private", label: "проекты, настройки и журналы доступны только участникам" }
    ],
    landingProblemTitle: "Единая среда для проверки API до готовности основного сервиса",
    landingProblemCopy: "После согласования API-контракта команде часто требуется стабильная среда для разработки интерфейсов, автотестов и интеграционных проверок. MSaaS предоставляет такую среду на основе спецификации, сохраняя историю запросов и настройки поведения mock-сервиса.",
    landingProblems: [
      { title: "Разработка клиентских приложений", body: "Команда получает стабильный mock URL и может выполнять реальные HTTP-запросы без ожидания развёртывания основного backend-сервиса." },
      { title: "Тестирование API-контрактов", body: "Опубликованный instance возвращает предсказуемые ответы, поддерживает ошибки, задержки и отдельные сценарии для проверки граничных состояний." },
      { title: "Демонстрационные окружения", body: "Для презентаций и внутренних проверок можно заранее подготовить нужные статусы, тела ответов и задержки без изменения кода приложения." }
    ],
    landingFeaturesTitle: "Основные возможности платформы",
    landingFeaturesCopy: "MSaaS фокусируется на управляемом жизненном цикле mock API: от загрузки спецификации до публикации URL, настройки сценариев, контроля доступа и анализа запросов.",
    landingFeatures: [
      { title: "Генерация ответов по контракту", body: "Сервис использует OpenAPI examples, named examples и schema-based generation, чтобы возвращать корректные JSON-ответы без ручной подготовки каждого payload." },
      { title: "Сценарии и правила ответов", body: "Для конкретных операций можно переопределять статус, тело, headers и задержку, а также применять декларативные правила к отдельным полям ответа." },
      { title: "Профили окружений", body: "Instance может работать в разных профилях, например для разработки, QA или демонстрационного стенда, без пересоздания публичной ссылки." },
      { title: "Наблюдаемость запросов", body: "Журналы показывают метод, путь, статус, matched/unmatched, источник ответа и применённые сценарии или правила." }
    ],
    landingWorkflowTitle: "Процесс работы",
    landingWorkflowCopy: "Платформа поддерживает последовательный workflow: проект, версия спецификации, проверка маршрутов, публикация instance и дальнейшее сопровождение запросов.",
    landingWorkflowSteps: [
      { title: "Создание проекта", body: "Проект объединяет спецификации, опубликованные mock-instance, участников, настройки доступа и историю запросов." },
      { title: "Загрузка OpenAPI", body: "Сервис нормализует маршруты, методы, параметры, request body, response examples и схемы ответов." },
      { title: "Предварительная проверка", body: "В preview можно выбрать route, status, content type, example и seed, чтобы проверить будущий ответ до публикации." },
      { title: "Публикация URL", body: "После публикации создаётся ссылка вида /mock/{token}/..., которую можно использовать в приложениях, Postman и автотестах." }
    ],
    landingSecurityTitle: "Контроль доступа к проектам и данным",
    landingSecurityCopy: "Каждый проект имеет владельца и участников с ролями. Публичный mock URL можно передавать внешним потребителям API, при этом спецификации, state, журналы и настройки остаются доступными только внутри проекта.",
    landingSecurityItems: [
      "Участников можно добавлять в проект",
      "Роли разделяют просмотр и управление",
      "Mock-ссылку можно пересоздать",
      "Логи и state видят только участники"
    ],
    landingMockStatusLabel: "Опубликованный instance",
    landingMockInstance: "orders-api / demo",
    landingMockStatus: "Готов",
    landingMockUrlLabel: "Public URL",
    landingMockUrl: "http://localhost:8081/mock/tk_demo/orders",
    landingMockStats: [
      { value: "43", label: "запроса за сегодня" },
      { value: "8 мс", label: "среднее время ответа" },
      { value: "2", label: "несовпадения с контрактом" }
    ],
    landingMockLogTitle: "Последние запросы",
    landingMockLogHint: "последние вызовы",
    landingMockRequests: [
      { path: "GET /orders", status: "200" },
      { path: "POST /orders", status: "201" },
      { path: "GET /orders/42", status: "404" }
    ],
    landingDiagnosticsPanelTitle: "Запись в логах",
    landingDiagnosticsPanelStatus: "unmatched",
    landingDiagnosticsRequestLabel: "Вызов",
    landingDiagnosticsReasonLabel: "Результат обработки",
    landingDiagnosticsReason: "status 404 · matched: false · source: fallback",
    landingDiagnosticsSpecLabel: "Контекст",
    landingDiagnosticsSpecValue: "demo profile · orders-api@2.1.0",
    landingDiagnosticsCta: "Проверить спецификацию",
    landingDiagnosticsTitle: "Диагностика запросов и источников ответа",
    landingDiagnosticsCopy: "Журнал фиксирует method, path, status, matched/unmatched, активный профиль и источник ответа: OpenAPI example, schema generation, scenario, response rule или fallback.",
    landingFinalTitle: "Подготовьте управляемый mock API для команды",
    landingFinalCopy: "Загрузите OpenAPI-спецификацию, опубликуйте instance и используйте стабильный URL для разработки, тестирования и демонстрационных окружений."
  },
  en: {
    access: "Access",
    account: "Account",
    accountFound: "Confirm sign-in",
    accountNew: "Create account",
    back: "Back",
    continue: "Continue",
    diagnostics: "Diagnostics",
    email: "Email",
    emailOrUsername: "Email or username",
    language: "Language",
    login: "Login",
    password: "Password",
    register: "Register",
    signedIn: "Signed in",
    theme: "Theme",
    username: "Username",
    passwordHint: "The account was found. Enter the password to open the console.",
    registerHint: "No account exists for this identifier. Complete the fields to register.",
    subtitle: "Console for private OpenAPI projects, mock links, response scenarios, and logs.",
    authTitle: "Sign in to the mock API console",
    authCopy: "Use your email or username. If the account exists, the console will request the password; otherwise it will continue with registration using the same identifier.",
    authEntryTitle: "Sign in or create account",
    authFlow: ["OpenAPI contracts", "Private projects", "Runtime and scenarios", "Logs and audit"],
    authBenefits: ["Role-based project access", "Specification and instance management", "Request and activity history"],
    landingNavFeatures: "Features",
    landingNavWorkflow: "How it works",
    landingNavSecurity: "Access",
    landingHeroBadge: "Managed mock API platform for OpenAPI contracts",
    heroTitle: "Mock services in one click",
    heroCopy: "MSaaS turns OpenAPI specifications into managed mock services with private projects, response scenarios, environment profiles, and request logs. Teams can validate integrations before the primary backend service is available.",
    landingPrimaryCta: "Create project",
    landingSecondaryCta: "Open console",
    landingHeroProofs: ["Private projects", "Published by secure link", "Audit and request logs"],
    landingAudience: ["Development", "QA", "Integrations", "Demo environments"],
    landingProofLabel: "Key benefits",
    landingMetrics: [
      { value: "OpenAPI", label: "the contract becomes the source of a managed mock service" },
      { value: "1 URL", label: "for client applications, automated tests, and integration checks" },
      { value: "Profiles", label: "separate behavior modes for development, QA, and demos" },
      { value: "Private", label: "projects, settings, and logs are available only to members" }
    ],
    landingProblemTitle: "A controlled API environment before the primary service is ready",
    landingProblemCopy: "After an API contract is approved, teams often need a stable environment for interface development, automated tests, and integration checks. MSaaS provides that environment from the specification while preserving request history and mock behavior settings.",
    landingProblems: [
      { title: "Client application development", body: "The team receives a stable mock URL and can execute real HTTP requests without waiting for the primary backend deployment." },
      { title: "API contract testing", body: "A published instance returns predictable responses and supports errors, delays, and scenarios for edge-case verification." },
      { title: "Demonstration environments", body: "Required statuses, response bodies, and latency settings can be prepared in advance without changing application code." }
    ],
    landingFeaturesTitle: "Core platform capabilities",
    landingFeaturesCopy: "MSaaS supports the managed lifecycle of a mock API: specification upload, URL publication, scenario configuration, access control, and request analysis.",
    landingFeatures: [
      { title: "Contract-based responses", body: "The service uses OpenAPI examples, named examples, and schema-based generation to return valid JSON responses without manually preparing every payload." },
      { title: "Scenarios and response rules", body: "For specific operations, teams can override status, body, headers, and delay, or apply declarative rules to individual response fields." },
      { title: "Environment profiles", body: "An instance can use different behavior profiles for development, QA, or demonstration environments without changing the public URL." },
      { title: "Request observability", body: "Logs show method, path, status, matched/unmatched, response source, and applied scenarios or rules." }
    ],
    landingWorkflowTitle: "Workflow",
    landingWorkflowCopy: "The platform follows a structured workflow: project, specification version, route validation, instance publication, and request monitoring.",
    landingWorkflowSteps: [
      { title: "Create a project", body: "A project keeps specifications, published mock instances, members, access settings, and request history together." },
      { title: "Upload OpenAPI", body: "The service normalizes routes, methods, parameters, request bodies, response examples, and response schemas." },
      { title: "Preview responses", body: "Preview lets users choose route, status, content type, example, and seed before publishing the instance." },
      { title: "Publish the URL", body: "After publication, a /mock/{token}/... link is available for applications, Postman, and automated tests." }
    ],
    landingSecurityTitle: "Project and data access control",
    landingSecurityCopy: "Each project has an owner and role-based members. The public mock URL can be shared with API consumers, while specifications, state, logs, and settings remain available only inside the project.",
    landingSecurityItems: [
      "Members can be added to a project",
      "Roles separate viewing and management",
      "Mock links can be rotated",
      "Logs and state are visible only to members"
    ],
    landingMockStatusLabel: "Published instance",
    landingMockInstance: "orders-api / demo",
    landingMockStatus: "Ready",
    landingMockUrlLabel: "Public URL",
    landingMockUrl: "http://localhost:8081/mock/tk_demo/orders",
    landingMockStats: [
      { value: "43", label: "requests today" },
      { value: "8 ms", label: "average response time" },
      { value: "2", label: "contract mismatches" }
    ],
    landingMockLogTitle: "Latest requests",
    landingMockLogHint: "latest calls",
    landingMockRequests: [
      { path: "GET /orders", status: "200" },
      { path: "POST /orders", status: "201" },
      { path: "GET /orders/42", status: "404" }
    ],
    landingDiagnosticsPanelTitle: "Log entry",
    landingDiagnosticsPanelStatus: "unmatched",
    landingDiagnosticsRequestLabel: "Call",
    landingDiagnosticsReasonLabel: "Processing result",
    landingDiagnosticsReason: "status 404 · matched: false · source: fallback",
    landingDiagnosticsSpecLabel: "Context",
    landingDiagnosticsSpecValue: "demo profile · orders-api@2.1.0",
    landingDiagnosticsCta: "Validate a specification",
    landingDiagnosticsTitle: "Request diagnostics and response source tracking",
    landingDiagnosticsCopy: "The request log records method, path, status, matched/unmatched, active profile, and response source: OpenAPI example, schema generation, scenario, response rule, or fallback.",
    landingFinalTitle: "Prepare a managed mock API for your team",
    landingFinalCopy: "Upload an OpenAPI specification, publish an instance, and use a stable URL for development, testing, and demonstration environments."
  }
} as const;

function PublicApp() {
  const [theme, setTheme] = useState<Theme>(() => (localStorage.getItem("msaas.theme") as Theme) || "light");
  const [lang, setLang] = useState<Lang>(() => (localStorage.getItem("msaas.lang") as Lang) || "ru");
  const [token, setToken] = useState(() => localStorage.getItem("msaas.token") ?? "");
  const [authStep, setAuthStep] = useState<AuthStep>(() => publicRoute(window.location.pathname).authStep);
  const [publicView, setPublicView] = useState<PublicView>(() => publicRoute(window.location.pathname).view);
  const [authIdentifier, setAuthIdentifier] = useState("demo@example.com");
  const [email, setEmail] = useState("demo@example.com");
  const [username, setUsername] = useState("demo");
  const [password, setPassword] = useState("password");
  const [busy, setBusy] = useState<Record<string, boolean>>({});
  const [notice, setNotice] = useState("");
  const t = publicText[lang];
  const isConsoleRoute = window.location.pathname.startsWith("/console");

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("msaas.theme", theme);
  }, [theme]);

  useEffect(() => {
    document.documentElement.lang = lang;
    localStorage.setItem("msaas.lang", lang);
  }, [lang]);

  useEffect(() => {
    const syncPublicState = () => {
      const route = publicRoute(window.location.pathname);
      setPublicView(route.view);
      setAuthStep(route.authStep);
      setToken(localStorage.getItem("msaas.token") ?? "");
    };
    window.addEventListener("storage", syncPublicState);
    window.addEventListener("msaas-auth-change", syncPublicState);
    window.addEventListener("popstate", syncPublicState);
    return () => {
      window.removeEventListener("storage", syncPublicState);
      window.removeEventListener("msaas-auth-change", syncPublicState);
      window.removeEventListener("popstate", syncPublicState);
    };
  }, []);

  useEffect(() => {
    if (!token && window.location.pathname.startsWith("/console")) {
      writeRoute("/login", true);
      setPublicView("auth");
      setAuthStep("identifier");
    }
  }, [token]);

  const shellControls = useMemo(() => (
    <div className="shell-controls" aria-label="preferences">
      <button className="icon-button" title={t.theme} onClick={() => setTheme(theme === "light" ? "dark" : "light")}>
        {theme === "light" ? <Moon size={18} /> : <Sun size={18} />}
      </button>
      <button className="segmented-button" title={t.language} onClick={() => setLang(lang === "ru" ? "en" : "ru")}>
        <Globe2 size={16} />
        {lang.toUpperCase()}
      </button>
    </div>
  ), [lang, t.language, t.theme, theme]);

  if (token && isConsoleRoute) {
    return (
      <Suspense fallback={<ConsoleFallback text={lang === "ru" ? "Загружаем консоль" : "Loading console"} />}>
        <ConsoleApp />
      </Suspense>
    );
  }

  if (token && (window.location.pathname === "/login" || window.location.pathname === "/register")) {
    writeRoute("/console/overview", true);
    return (
      <Suspense fallback={<ConsoleFallback text={lang === "ru" ? "Загружаем консоль" : "Loading console"} />}>
        <ConsoleApp />
      </Suspense>
    );
  }

  if (publicView === "auth") {
    return (
      <main className="auth-shell">
        <header className="auth-topbar">
          <Brand />
          <div className="shell-controls">
            {shellControls}
            <button className="soft-button" onClick={() => openLanding()}>{t.back}</button>
          </div>
        </header>
        <section className="auth-grid">
          <div className="auth-copy">
            <p className="eyebrow">MSaaS Console</p>
            <h1>{t.authTitle}</h1>
            <p className="auth-lead">{t.authCopy}</p>
            <div className="pipeline" aria-label="pipeline">
              {t.authFlow.map((label, index) => (
                <FlowStep
                  key={label}
                  icon={[<FileJson size={20} />, <ShieldCheck size={20} />, <Zap size={20} />, <Activity size={20} />][index]}
                  label={label}
                />
              ))}
            </div>
            <div className="auth-benefits">
              {t.authBenefits.map((item) => (
                <span key={item}><CheckCircle2 size={15} />{item}</span>
              ))}
            </div>
          </div>
          <section className="auth-panel" aria-label={t.access}>
            <div>
              <p className="eyebrow">{t.account}</p>
              <h2>{authStep === "register" ? t.accountNew : authStep === "password" ? t.accountFound : t.authEntryTitle}</h2>
            </div>
            {authStep === "identifier" && (
              <label>{t.emailOrUsername}<input value={authIdentifier} onChange={(event) => setAuthIdentifier(event.target.value)} /></label>
            )}
            {authStep === "password" && (
              <>
                <p className="muted">{t.passwordHint}</p>
                <Detail label={t.emailOrUsername} value={authIdentifier} />
                <label>{t.password}<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
              </>
            )}
            {authStep === "register" && (
              <>
                <p className="muted">{t.registerHint}</p>
                <label>{t.email}<input value={email} onChange={(event) => setEmail(event.target.value)} /></label>
                <label>{t.username}<input value={username} onChange={(event) => setUsername(event.target.value)} /></label>
                <label>{t.password}<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
              </>
            )}
            <div className="button-row">
              {authStep === "identifier" && (
                <button className="primary-button" onClick={lookupAccount} disabled={busy["auth-lookup"]}>
                  {busy["auth-lookup"] ? <Loader2 className="spin" size={17} /> : <KeyRound size={17} />}
                  {t.continue}
                </button>
              )}
              {authStep === "password" && (
                <button className="primary-button" onClick={() => authenticate("/api/auth/login")} disabled={busy.auth}>
                  {busy.auth ? <Loader2 className="spin" size={17} /> : <KeyRound size={17} />}
                  {t.login}
                </button>
              )}
              {authStep === "register" && (
                <button className="primary-button" onClick={() => authenticate("/api/auth/register")} disabled={busy.auth}>
                  {busy.auth ? <Loader2 className="spin" size={17} /> : <Plus size={17} />}
                  {t.register}
                </button>
              )}
              {authStep === "identifier" ? (
                <button className="soft-button" onClick={openLanding}>{t.back}</button>
              ) : (
                <button className="soft-button" onClick={openLogin}>{t.back}</button>
              )}
            </div>
            {notice && <p className="notice">{notice}</p>}
          </section>
        </section>
      </main>
    );
  }

  return renderLanding();

  function renderLanding() {
    return (
      <main className="public-shell landing-shell">
        <header className="auth-topbar landing-topbar">
          <Brand />
          <nav className="landing-nav" aria-label="landing navigation">
            <a href="#landing-features">{t.landingNavFeatures}</a>
            <a href="#landing-workflow">{t.landingNavWorkflow}</a>
            <a href="#landing-diagnostics">{t.diagnostics}</a>
          </nav>
          <div className="shell-controls" aria-label="preferences">
            {shellControls}
            <button className="soft-button" onClick={openLogin}>{t.login}</button>
            <button className="primary-button" onClick={openRegister}>{t.register}</button>
          </div>
        </header>
        <section className="landing-hero">
          <div className="landing-hero-backdrop" aria-hidden="true" />
          <div className="landing-copy">
            <p className="eyebrow">{t.landingHeroBadge}</p>
            <h1>{t.heroTitle}</h1>
            <p>{t.heroCopy}</p>
            <div className="landing-hero-actions">
              <button className="primary-button" onClick={openRegister}><KeyRound size={17} />{t.landingPrimaryCta}</button>
              <button className="soft-button" onClick={openLogin}><Plus size={17} />{t.landingSecondaryCta}</button>
            </div>
            <div className="hero-proof-row">
              {t.landingHeroProofs.map((item) => (
                <span key={item}><CheckCircle2 size={15} />{item}</span>
              ))}
            </div>
            <div className="landing-audience-row" aria-label={lang === "ru" ? "Для кого" : "Use cases"}>
              {t.landingAudience.map((item) => (
                <span key={item}>{item}</span>
              ))}
            </div>
          </div>
          <div className="landing-dashboard-card" aria-hidden="true">
            <div className="landing-dashboard-inner">
              <div className="dashboard-head">
                <span>
                  <small>{t.landingMockStatusLabel}</small>
                  <strong>{t.landingMockInstance}</strong>
                </span>
                <StatusPill ok text={t.landingMockStatus} />
              </div>
              <div className="dashboard-url">
                <small>{t.landingMockUrlLabel}</small>
                <code>{t.landingMockUrl}</code>
              </div>
              <div className="dashboard-stats">
                {t.landingMockStats.map((item) => (
                  <div key={item.label}>
                    <strong>{item.value}</strong>
                    <small>{item.label}</small>
                  </div>
                ))}
              </div>
              <div className="dashboard-log">
                <div className="dashboard-log-head">
                  <strong>{t.landingMockLogTitle}</strong>
                  <small>{t.landingMockLogHint}</small>
                </div>
                {t.landingMockRequests.map((row) => (
                  <div className={`dashboard-log-row ${row.status.startsWith("4") ? "error" : ""}`} key={`${row.path}-${row.status}`}>
                    <code>{row.path}</code>
                    <span>{row.status}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>
        <section className="landing-metrics" aria-label={t.landingProofLabel}>
          {t.landingMetrics.map((item) => (
            <article className="landing-metric" key={item.label}>
              <strong>{item.value}</strong>
              <span>{item.label}</span>
            </article>
          ))}
        </section>
        <section className="landing-section landing-problem">
          <div className="landing-section-heading">
            <p className="eyebrow">{lang === "ru" ? "Зачем это нужно" : "Why MSaaS"}</p>
            <h2>{t.landingProblemTitle}</h2>
            <p>{t.landingProblemCopy}</p>
          </div>
          <div className="landing-card-grid">
            {t.landingProblems.map((item, index) => (
              <LandingCard
                key={item.title}
                icon={[<Server size={19} />, <Users size={19} />, <Activity size={19} />][index]}
                title={item.title}
                body={item.body}
              />
            ))}
          </div>
        </section>
        <section className="landing-band" id="landing-features">
          <div className="landing-section">
            <div className="landing-section-heading">
              <p className="eyebrow">{t.landingNavFeatures}</p>
              <h2>{t.landingFeaturesTitle}</h2>
              <p>{t.landingFeaturesCopy}</p>
            </div>
            <div className="landing-feature-grid">
              {t.landingFeatures.map((item, index) => (
                <LandingCard
                  key={item.title}
                  icon={[<FileJson size={19} />, <KeyRound size={19} />, <Layers3 size={19} />, <Activity size={19} />][index]}
                  title={item.title}
                  body={item.body}
                />
              ))}
            </div>
          </div>
        </section>
        <section className="landing-section landing-workflow" id="landing-workflow">
          <div className="landing-section-heading">
            <p className="eyebrow">{t.landingNavWorkflow}</p>
            <h2>{t.landingWorkflowTitle}</h2>
            <p>{t.landingWorkflowCopy}</p>
          </div>
          <div className="landing-step-grid">
            {t.landingWorkflowSteps.map((item, index) => (
              <article className="landing-step" key={item.title}>
                <span>{index + 1}</span>
                <strong>{item.title}</strong>
                <p>{item.body}</p>
              </article>
            ))}
          </div>
        </section>
        <section className="landing-section landing-diagnostics" id="landing-diagnostics">
          <div className="diagnostics-preview">
            <div className="diagnostics-preview-inner">
              <div className="diagnostics-preview-head">
                <strong>{t.landingDiagnosticsPanelTitle}</strong>
                <span>{t.landingDiagnosticsPanelStatus}</span>
              </div>
              <div className="diagnostics-field">
                <small>{t.landingDiagnosticsRequestLabel}</small>
                <code>GET /orders/42</code>
              </div>
              <div className="diagnostics-field">
                <small>{t.landingDiagnosticsReasonLabel}</small>
                <p>{t.landingDiagnosticsReason}</p>
              </div>
              <div className="diagnostics-field">
                <small>{t.landingDiagnosticsSpecLabel}</small>
                <code>{t.landingDiagnosticsSpecValue}</code>
              </div>
            </div>
          </div>
          <div className="landing-section-heading">
            <p className="eyebrow">{t.diagnostics}</p>
            <h2>{t.landingDiagnosticsTitle}</h2>
            <p>{t.landingDiagnosticsCopy}</p>
            <button className="primary-button" onClick={openRegister}>
              <KeyRound size={17} />
              {t.landingDiagnosticsCta}
            </button>
          </div>
        </section>
        <section className="landing-band" id="landing-security">
          <div className="landing-section landing-security">
            <div className="landing-section-heading">
              <p className="eyebrow">{t.landingNavSecurity}</p>
              <h2>{t.landingSecurityTitle}</h2>
              <p>{t.landingSecurityCopy}</p>
            </div>
            <div className="security-list">
              {t.landingSecurityItems.map((item) => (
                <span key={item}><CheckCircle2 size={17} />{item}</span>
              ))}
            </div>
          </div>
        </section>
        <section className="landing-final">
          <div>
            <p className="eyebrow">MSaaS Console</p>
            <h2>{t.landingFinalTitle}</h2>
            <p>{t.landingFinalCopy}</p>
          </div>
          <div className="landing-hero-actions">
            <button className="primary-button" onClick={openRegister}><KeyRound size={17} />{t.landingPrimaryCta}</button>
            <button className="soft-button" onClick={openLogin}><Plus size={17} />{t.landingSecondaryCta}</button>
          </div>
        </section>
      </main>
    );
  }

  function openLanding() {
    setPublicView("landing");
    setAuthStep("identifier");
    setNotice("");
    writeRoute("/");
  }

  function openLogin() {
    setPublicView("auth");
    setAuthStep("identifier");
    setNotice("");
    writeRoute("/login");
  }

  function openRegister() {
    setPublicView("auth");
    setAuthStep("register");
    setNotice("");
    writeRoute("/register");
  }

  async function run(key: string, action: () => Promise<void>) {
    setBusy((current) => ({ ...current, [key]: true }));
    try {
      await action();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : String(error));
    } finally {
      setBusy((current) => ({ ...current, [key]: false }));
    }
  }

  async function lookupAccount() {
    const identifier = authIdentifier.trim();
    if (!identifier) {
      setNotice(t.emailOrUsername);
      return;
    }
    await run("auth-lookup", async () => {
      const data = await api<AuthLookup>("/api/auth/lookup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ identifier })
      });
      setNotice("");
      if (data.exists) {
        setAuthStep("password");
        writeRoute("/login", true);
        if (data.username) setUsername(data.username);
      } else {
        setAuthStep("register");
        writeRoute("/register", true);
        if (identifier.includes("@")) {
          setEmail(identifier);
          setUsername(suggestUsername(identifier));
        } else {
          setUsername(identifier);
          setEmail("");
        }
      }
    });
  }

  async function authenticate(path: "/api/auth/login" | "/api/auth/register") {
    await run("auth", async () => {
      const body = path === "/api/auth/register"
        ? { email, username, password }
        : { identifier: authIdentifier, password };
      const data = await api<AuthResponse>(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      localStorage.setItem("msaas.token", data.token);
      localStorage.setItem("msaas.user", JSON.stringify(data.user));
      setToken(data.token);
      setNotice(`${t.signedIn}: ${displayUser(data.user)}`);
      writeRoute("/console/overview", true);
      window.dispatchEvent(new Event("msaas-auth-change"));
    });
  }
}

async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init);
  const textBody = await response.text();
  const body = textBody ? JSON.parse(textBody) : null;
  if (!response.ok) {
    const details = Array.isArray(body?.details) && body.details.length ? `: ${body.details.join(", ")}` : "";
    throw new Error(`${body?.error ?? response.statusText}${details}`);
  }
  return body as T;
}

function publicRoute(pathname: string): { view: PublicView; authStep: AuthStep } {
  const normalized = pathname.replace(/\/+$/, "") || "/";
  if (normalized === "/register") return { view: "auth", authStep: "register" };
  if (normalized === "/login" || normalized.startsWith("/console")) return { view: "auth", authStep: "identifier" };
  return { view: "landing", authStep: "identifier" };
}

function writeRoute(path: string, replace = false) {
  if (window.location.pathname === path) return;
  const method = replace ? "replaceState" : "pushState";
  window.history[method](null, "", path);
}

function displayUser(value: { username?: string | null; email?: string | null }) {
  if (value.username && value.username.trim()) return value.username;
  return value.email ? value.email.split("@", 1)[0] : "unknown";
}

function suggestUsername(identifier: string) {
  const raw = identifier.includes("@") ? identifier.split("@", 1)[0] : identifier;
  const normalized = raw.toLowerCase().replace(/[^a-z0-9_.-]/g, "-").replace(/^[._-]+|[._-]+$/g, "");
  return normalized.length >= 3 ? normalized.slice(0, 28) : "user";
}

function Brand() {
  return (
    <a className="brand" href="/" aria-label="MSaaS home">
      <span className="brand-mark"><Server size={20} /></span>
      <span>
        <strong>MSaaS Console</strong>
        <small>Мок-сервисы за один клик</small>
      </span>
    </a>
  );
}

function FlowStep({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <div className="flow-step">
      {icon}
      <strong>{label}</strong>
    </div>
  );
}

function LandingCard({ icon, title, body }: { icon: React.ReactNode; title: string; body: string }) {
  return (
    <article className="landing-card">
      <span>{icon}</span>
      <strong>{title}</strong>
      <p>{body}</p>
    </article>
  );
}

function StatusPill({ ok, text }: { ok: boolean; text: string }) {
  return <span className={`status-pill ${ok ? "success" : "danger"}`}>{text}</span>;
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="detail">
      <small>{label}</small>
      <strong>{value}</strong>
    </div>
  );
}

function ConsoleFallback({ text }: { text: string }) {
  return (
    <main className="auth-shell">
      <header className="auth-topbar">
        <Brand />
      </header>
      <section className="empty-state">
        <Loader2 className="spin" size={28} />
        <strong>{text}</strong>
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(<PublicApp />);
