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
    accountFound: "Аккаунт найден",
    accountNew: "Создать аккаунт",
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
    passwordHint: "Введите пароль для найденного аккаунта.",
    registerHint: "Такого пользователя пока нет. Заполните email, никнейм и пароль, чтобы создать аккаунт.",
    subtitle: "Консоль для приватных OpenAPI-проектов, mock-ссылок, сценариев ответов и логов.",
    landingNavFeatures: "Возможности",
    landingNavWorkflow: "Как работает",
    landingNavSecurity: "Доступ",
    landingHeroBadge: "Когда контракт уже есть, а стенда ещё нет",
    heroTitle: "Backend ещё в работе. API уже можно дать команде.",
    heroCopy: "Загрузите OpenAPI-файл, и MSaaS поднимет mock-адрес для проекта. Фронтенд подключается к нему как к обычному API, QA проверяет сценарии, а в логах видно, что пришло и почему вернулся именно такой ответ.",
    landingPrimaryCta: "Собрать первый mock",
    landingSecondaryCta: "Войти в консоль",
    landingHeroProofs: ["Один URL для всей команды", "Ответы берутся из OpenAPI", "Логи без догадок"],
    landingAudience: ["Frontend", "QA", "Демо", "Интеграции"],
    landingProofLabel: "Ключевые преимущества",
    landingMetrics: [
      { value: "5 минут", label: "обычно хватает, чтобы получить первый рабочий URL" },
      { value: "1 ссылка", label: "для фронтенда, автотестов и демо" },
      { value: "Без копий", label: "не нужно пересылать JSON-файлы и держать разные версии ответа" },
      { value: "Приватно", label: "проект и логи видят только участники" }
    ],
    landingProblemTitle: "Обычно всё тормозит не из-за кода, а из-за ожидания стенда",
    landingProblemCopy: "Контракт уже согласован, макеты готовы, задачи взяты в работу, но нормального API ещё нет. В итоге появляются временные JSON-файлы, локальные заглушки и долгие вопросы о том, что именно отправлял клиент. MSaaS закрывает этот промежуток.",
    landingProblems: [
      { title: "Фронтенду нужен живой адрес", body: "Не надо ждать деплой backend-сервиса. Подставляете mock URL в приложение и проверяете реальные HTTP-запросы." },
      { title: "QA хочет повторяемый стенд", body: "Один опубликованный instance ведёт себя одинаково для всех. Ошибки, задержки и нестандартные ответы включаются из консоли." },
      { title: "Демо лучше готовить заранее", body: "Нужен успешный путь, ошибка или медленный ответ: включаете нужный сценарий в консоли и спокойно показываете." }
    ],
    landingFeaturesTitle: "Что помогает в обычной работе",
    landingFeaturesCopy: "MSaaS нужен не для красивой витрины, а для дней, когда контракт уже согласован, а настоящий сервис ещё не готов. Команда получает один адрес, понятные ответы и историю вызовов.",
    landingFeatures: [
      { title: "Ответы из контракта", body: "Если в OpenAPI есть example, сервис вернёт его. Если примера нет, соберёт аккуратный JSON по schema, чтобы клиенту было с чем работать." },
      { title: "Сценарии без правки кода", body: "Нужно проверить пустой список, ошибку оплаты или задержку? Выбираете route и задаёте нужный ответ в консоли." },
      { title: "Разные режимы для команды", body: "Для разработки можно оставить быстрые ответы, для QA включить ошибки, для демо подготовить стабильный happy path." },
      { title: "Логи без угадывания", body: "Видно, какой запрос пришёл, какой статус ушёл обратно и был ли найден подходящий маршрут в контракте." }
    ],
    landingWorkflowTitle: "Как это выглядит в работе",
    landingWorkflowCopy: "Всё укладывается в обычный рабочий поток: создали проект, загрузили OpenAPI, проверили preview и отдали ссылку команде.",
    landingWorkflowSteps: [
      { title: "Создаёте проект", body: "Проект хранит спецификации, опубликованные mock-и, доступы и историю запросов." },
      { title: "Загружаете OpenAPI", body: "Сервис разбирает routes, методы, параметры, body и доступные ответы." },
      { title: "Проверяете ответ", body: "В preview можно выбрать route, status и seed, чтобы понять, что увидит клиент." },
      { title: "Публикуете URL", body: "Ссылка вида /mock/{token}/... сразу готова для приложения, Postman или автотестов." }
    ],
    landingSecurityTitle: "Проект можно открыть команде, не отдавая управление всем подряд",
    landingSecurityCopy: "Владелец добавляет участников в проект и выбирает роль. Mock URL можно дать тем, кто должен вызывать API, а спецификации, state, логи и настройки остаются внутри рабочей области.",
    landingSecurityItems: [
      "Участников можно добавлять в проект",
      "Роли разделяют просмотр и управление",
      "Mock-ссылку можно пересоздать",
      "Логи и state видят только участники"
    ],
    landingMockStatusLabel: "Опубликованный mock",
    landingMockInstance: "orders-api / demo",
    landingMockStatus: "Готов",
    landingMockUrlLabel: "Public URL",
    landingMockUrl: "http://localhost:8081/mock/tk_demo/orders",
    landingMockStats: [
      { value: "43", label: "вызова сегодня" },
      { value: "8 мс", label: "обычный ответ" },
      { value: "2", label: "mismatch" }
    ],
    landingMockLogTitle: "Что пришло сейчас",
    landingMockLogHint: "последние вызовы",
    landingMockRequests: [
      { path: "GET /orders", status: "200" },
      { path: "POST /orders", status: "201" },
      { path: "GET /orders/42", status: "404" }
    ],
    landingDiagnosticsPanelTitle: "Запись в логах",
    landingDiagnosticsPanelStatus: "unmatched",
    landingDiagnosticsRequestLabel: "Вызов",
    landingDiagnosticsReasonLabel: "Что видно",
    landingDiagnosticsReason: "status 404 · matched: false · source: fallback",
    landingDiagnosticsSpecLabel: "Контекст",
    landingDiagnosticsSpecValue: "demo profile · orders-api@2.1.0",
    landingDiagnosticsCta: "Проверить на своём контракте",
    landingDiagnosticsTitle: "Логи показывают, что реально произошло",
    landingDiagnosticsCopy: "Когда интерфейс получил 404 или неожиданный JSON, можно открыть лог и увидеть метод, путь, статус, совпал ли маршрут и откуда взялся ответ: example, scenario, rule или fallback.",
    landingFinalTitle: "Дайте команде стабильный mock API без лишней ручной работы",
    landingFinalCopy: "Загрузите OpenAPI-файл и получите адрес, который можно отдать frontend, QA и демо-стенду уже сегодня."
  },
  en: {
    access: "Access",
    account: "Account",
    accountFound: "Account found",
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
    passwordHint: "Enter the password for the account we found.",
    registerHint: "No user exists yet. Add email, username, and password to create an account.",
    subtitle: "Console for private OpenAPI projects, mock links, response scenarios, and logs.",
    landingNavFeatures: "Features",
    landingNavWorkflow: "How it works",
    landingNavSecurity: "Access",
    landingHeroBadge: "For the gap between signed-off contract and working environment",
    heroTitle: "The backend is still in progress. The API can already be used.",
    heroCopy: "Upload an OpenAPI file and MSaaS gives the project a mock URL. Frontend uses it like a normal API, QA runs scenarios, and logs show what arrived and why that response went out.",
    landingPrimaryCta: "Build the first mock",
    landingSecondaryCta: "Open console",
    landingHeroProofs: ["One URL for the team", "Responses come from OpenAPI", "Logs explain what happened"],
    landingAudience: ["Frontend", "QA", "Demos", "Integrations"],
    landingProofLabel: "Key benefits",
    landingMetrics: [
      { value: "5 min", label: "usually enough to get the first callable URL" },
      { value: "1 link", label: "for frontend, tests, and demos" },
      { value: "No copies", label: "no JSON files passed around with different response versions" },
      { value: "Private", label: "only members see the project and logs" }
    ],
    landingProblemTitle: "Teams usually slow down while waiting for the test environment",
    landingProblemCopy: "The contract is agreed, screens are ready, tickets are already in progress, but there is still no real API to call. That is when temporary JSON files, local stubs, and long questions about what the client sent appear. MSaaS covers that gap.",
    landingProblems: [
      { title: "Frontend needs a real address", body: "No need to wait for a backend deployment. Put the mock URL into the app and test real HTTP calls." },
      { title: "QA wants a repeatable target", body: "One published instance behaves the same for everyone. Errors, delays, and unusual responses are turned on from the console." },
      { title: "Demos are better prepared", body: "Need a happy path, an error, or a slow response? Turn on the right scenario in the console and show it calmly." }
    ],
    landingFeaturesTitle: "What helps in everyday work",
    landingFeaturesCopy: "MSaaS is for the days when the contract is agreed, but the real service is not ready yet. The team gets one address, understandable responses, and request history.",
    landingFeatures: [
      { title: "Responses from the contract", body: "If OpenAPI has an example, the service returns it. If not, it builds clean JSON from the schema so the client has something useful to call." },
      { title: "Scenarios without code changes", body: "Need an empty list, payment error, or delay? Pick a route and set the response in the console." },
      { title: "Different modes for the team", body: "Development can stay fast, QA can enable errors, and demos can keep a stable happy path." },
      { title: "Logs without guessing", body: "See which request arrived, which status went back, and whether a matching route was found in the contract." }
    ],
    landingWorkflowTitle: "What the workflow looks like",
    landingWorkflowCopy: "It fits into the normal workflow: create a project, upload OpenAPI, check the preview, and share the link with the team.",
    landingWorkflowSteps: [
      { title: "Create a project", body: "A project keeps specs, published mocks, access, and request history together." },
      { title: "Upload OpenAPI", body: "The service reads routes, methods, parameters, body, and available responses." },
      { title: "Check the response", body: "Use preview to choose route, status, and seed before the client calls it." },
      { title: "Publish the URL", body: "A /mock/{token}/... link is ready for the app, Postman, or automated tests." }
    ],
    landingSecurityTitle: "Open the project to the team without giving everyone full control",
    landingSecurityCopy: "The owner adds members to a project and chooses their role. The mock URL can be shared with people who need to call the API, while specs, state, logs, and settings stay inside the workspace.",
    landingSecurityItems: [
      "Members can be added to a project",
      "Roles separate viewing and management",
      "Mock links can be rotated",
      "Logs and state are visible only to members"
    ],
    landingMockStatusLabel: "Published mock",
    landingMockInstance: "orders-api / demo",
    landingMockStatus: "Ready",
    landingMockUrlLabel: "Public URL",
    landingMockUrl: "http://localhost:8081/mock/tk_demo/orders",
    landingMockStats: [
      { value: "43", label: "calls today" },
      { value: "8 ms", label: "usual response" },
      { value: "2", label: "mismatch" }
    ],
    landingMockLogTitle: "What just came in",
    landingMockLogHint: "latest calls",
    landingMockRequests: [
      { path: "GET /orders", status: "200" },
      { path: "POST /orders", status: "201" },
      { path: "GET /orders/42", status: "404" }
    ],
    landingDiagnosticsPanelTitle: "Log entry",
    landingDiagnosticsPanelStatus: "unmatched",
    landingDiagnosticsRequestLabel: "Call",
    landingDiagnosticsReasonLabel: "What you see",
    landingDiagnosticsReason: "status 404 · matched: false · source: fallback",
    landingDiagnosticsSpecLabel: "Context",
    landingDiagnosticsSpecValue: "demo profile · orders-api@2.1.0",
    landingDiagnosticsCta: "Try it with your contract",
    landingDiagnosticsTitle: "Logs show what actually happened",
    landingDiagnosticsCopy: "When the UI gets a 404 or unexpected JSON, open the log and see the method, path, status, whether the route matched, and where the response came from: example, scenario, rule, or fallback.",
    landingFinalTitle: "Give the team a stable mock API without extra manual work",
    landingFinalCopy: "Upload an OpenAPI file and get a URL you can give to frontend, QA, and demo environments today."
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
    const syncAuth = () => setToken(localStorage.getItem("msaas.token") ?? "");
    const onPopState = () => {
      const route = publicRoute(window.location.pathname);
      setPublicView(route.view);
      setAuthStep(route.authStep);
      syncAuth();
    };
    window.addEventListener("storage", syncAuth);
    window.addEventListener("msaas-auth-change", syncAuth);
    window.addEventListener("popstate", onPopState);
    return () => {
      window.removeEventListener("storage", syncAuth);
      window.removeEventListener("msaas-auth-change", syncAuth);
      window.removeEventListener("popstate", onPopState);
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
            <h1>{t.subtitle}</h1>
            <div className="pipeline" aria-label="pipeline">
              <FlowStep icon={<FileJson size={20} />} label="OpenAPI" />
              <FlowStep icon={<Zap size={20} />} label="Mock URL" />
              <FlowStep icon={<ShieldCheck size={20} />} label="Private project" />
              <FlowStep icon={<Activity size={20} />} label="Logs" />
            </div>
          </div>
          <section className="auth-panel" aria-label={t.access}>
            <div>
              <p className="eyebrow">{t.account}</p>
              <h2>{authStep === "register" ? t.accountNew : authStep === "password" ? t.accountFound : t.access}</h2>
            </div>
            {authStep === "identifier" && (
              <label>{t.emailOrUsername}<input value={authIdentifier} onChange={(event) => setAuthIdentifier(event.target.value)} autoFocus /></label>
            )}
            {authStep === "password" && (
              <>
                <p className="muted">{t.passwordHint}</p>
                <Detail label={t.emailOrUsername} value={authIdentifier} />
                <label>{t.password}<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoFocus /></label>
              </>
            )}
            {authStep === "register" && (
              <>
                <p className="muted">{t.registerHint}</p>
                <label>{t.email}<input value={email} onChange={(event) => setEmail(event.target.value)} autoFocus /></label>
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
    <div className="brand">
      <span className="brand-mark"><Server size={20} /></span>
      <span>
        <strong>MSaaS</strong>
        <small>Mock Server as a Service</small>
      </span>
    </div>
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
  return <span className={`status-pill ${ok ? "ok" : "bad"}`}>{text}</span>;
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
