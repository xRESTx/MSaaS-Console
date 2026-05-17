import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  AlertTriangle,
  Braces,
  CheckCircle2,
  Clipboard,
  Code2,
  Database,
  FileJson,
  Globe2,
  History,
  KeyRound,
  Layers3,
  ListFilter,
  Loader2,
  Lock,
  LogOut,
  Moon,
  Play,
  Plus,
  RefreshCw,
  RotateCw,
  Search,
  Send,
  Server,
  Settings2,
  ShieldCheck,
  Sun,
  Trash2,
  Upload,
  UserPlus,
  Users,
  XCircle,
  Zap
} from "lucide-react";
import "./styles.css";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";

type Lang = "ru" | "en";
type Theme = "light" | "dark";
type ViewKey = "overview" | "specs" | "runtime" | "logs" | "access" | "settings" | "admin";
type PublicView = "landing" | "auth";
type AuthStep = "identifier" | "password" | "register";
type ProjectRole = "OWNER" | "MEMBER" | "VIEWER";
type InstanceMode = "STATELESS" | "STATEFUL";
type SystemRole = "USER" | "ADMIN";

type User = {
  id: string;
  email: string;
  username: string;
  systemRole: SystemRole;
  disabled: boolean;
};

type Project = {
  id: string;
  ownerId: string;
  name: string;
  description: string;
  role: ProjectRole;
  memberCount: number;
};

type SpecVersion = {
  id: string;
  projectId: string;
  versionNumber: number;
  name: string;
  status: "VALID" | "INVALID";
  validationErrors: string[];
  routeCount: number;
  source: string;
};

type ContractRoute = {
  method: string;
  pathTemplate: string;
  operationId: string | null;
  requiredQueryParameters: string[];
  requiredHeaderParameters: string[];
  requestBodyRequired: boolean;
  responses: ContractResponse[];
};

type ContractResponse = {
  statusCode: number;
  contentType: string;
  contentTypes: string[];
  examples: string[];
  schemaAvailable: boolean;
};

type MockInstance = {
  id: string;
  projectId: string;
  specVersionId: string;
  publicUrl: string | null;
  tokenPreview: string;
  mockApiKey: string | null;
  apiKeyPreview: string | null;
  requireApiKey: boolean;
  mode: InstanceMode;
  status: string;
  routeCount: number;
  rateLimitEnabled: boolean;
  rateLimitRequests: number;
  rateLimitWindowSeconds: number;
  smartResponsesEnabled: boolean;
  smartSeedMode: string;
  scenarioCount: number;
  responseRuleCount: number;
  faultProfileEnabled: boolean;
  faultErrorRate: number;
  faultStatusCode: number;
  latencyMinMs: number;
  latencyMaxMs: number;
  activeProfile: string;
  activeProfileName: string;
  profileCount: number;
};

type MockScenario = {
  id: string;
  name: string;
  enabled: boolean;
  priority: number;
  operationId: string | null;
  method: string | null;
  pathTemplate: string | null;
  statusCode: number | null;
  contentType: string;
  body: unknown;
  headers: Record<string, string>;
  delayMs: number;
  createdAt: string;
  updatedAt: string;
};

type ResponseRule = {
  id: string;
  name: string;
  enabled: boolean;
  priority: number;
  operationId: string | null;
  method: string | null;
  pathTemplate: string | null;
  fieldPath: string;
  type: string;
  fixedValue: unknown;
  minValue: number | null;
  maxValue: number | null;
  enumValues: unknown[];
  template: string | null;
  createdAt: string;
  updatedAt: string;
};

type MockProfile = {
  id: string;
  name: string;
  faultProfileEnabled: boolean;
  faultErrorRate: number;
  faultStatusCode: number;
  latencyMinMs: number;
  latencyMaxMs: number;
  createdAt: string;
  updatedAt: string;
};

type ResponsePreview = {
  statusCode: number;
  contentType: string;
  body: unknown;
  generated: boolean;
  seed: string;
};

type RequestLog = {
  id: string;
  method: string;
  path: string;
  queryString: string | null;
  requestHeaders: Record<string, string>;
  requestBody: string | null;
  responseStatus: number;
  matched: boolean;
  error: string | null;
  responseSource: string;
  profileName: string | null;
  appliedRuleIds: string[];
  latencyMs: number;
  receivedAt: string;
};

type ProjectMember = {
  userId: string;
  email: string;
  username: string;
  role: ProjectRole;
  addedAt: string;
};

type AuditEvent = {
  id: string;
  actorId: string;
  action: string;
  targetType: string;
  targetId: string;
  message: string;
  metadata: Record<string, unknown>;
  createdAt: string;
};

type RuntimeWorker = {
  id: string;
  workerKey: string;
  baseUrl: string;
  status: "UP" | "DEGRADED" | "DOWN";
  slotCount: number;
  labels: Record<string, string>;
  lastHeartbeatAt: string;
};

type RuntimeSlot = {
  instanceId: string;
  projectId: string;
  tokenPreview: string;
  workerKey: string | null;
  mode: string;
  loadedAt: string;
  stateCollectionCount: number;
};

type AdminSummary = {
  users: number;
  projects: number;
  specVersions: number;
  invalidSpecVersions: number;
  instances: number;
  runningInstances: number;
  runtimeWorkers: number;
  runtimeSlots: number;
  requestLogs: number;
  serverErrors: number;
  unmatchedRequests: number;
  rateLimitEvents: number;
  unmatchedRatio: number;
  averageLatencyMs: number;
};

type AdminUser = {
  id: string;
  email: string;
  username: string;
  systemRole: SystemRole;
  disabled: boolean;
  ownedProjects: number;
  createdAt: string;
};

type AdminProject = {
  id: string;
  ownerId: string;
  ownerEmail: string;
  ownerUsername: string;
  name: string;
  description: string;
  memberCount: number;
  specVersionCount: number;
  instanceCount: number;
  createdAt: string;
  updatedAt: string;
};

type AdminInstance = {
  id: string;
  projectId: string;
  specVersionId: string;
  tokenPreview: string;
  apiKeyPreview: string | null;
  requireApiKey: boolean;
  mode: InstanceMode;
  status: string;
  routeCount: number;
  createdAt: string;
  updatedAt: string;
};

type AdminLog = {
  id: string;
  projectId: string;
  projectName: string;
  ownerId: string | null;
  ownerEmail: string;
  ownerUsername: string;
  instanceId: string;
  method: string;
  path: string;
  queryString: string | null;
  responseStatus: number;
  matched: boolean;
  error: string | null;
  responseSource: string;
  profileName: string | null;
  appliedRuleIds: string[];
  latencyMs: number;
  receivedAt: string;
};

type AdminAudit = {
  id: string;
  projectId: string;
  actorId: string;
  actorEmail: string;
  actorUsername: string;
  action: string;
  targetType: string;
  targetId: string;
  message: string;
  metadata: Record<string, unknown>;
  createdAt: string;
};

type AdminPage<T> = {
  items: T[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
};

type AuthResponse = {
  token: string;
  user: User;
};

type AuthLookup = {
  exists: boolean;
  identifier: string;
  username: string | null;
};

type ToastMessage = {
  id: number;
  text: string;
};

type DeleteTarget =
  | { kind: "project"; id: string; name: string }
  | { kind: "spec"; id: string; name: string }
  | { kind: "instance"; id: string; name: string }
  | { kind: "member"; id: string; name: string }
  | { kind: "admin-user"; id: string; name: string }
  | { kind: "admin-project"; id: string; name: string }
  | { kind: "admin-instance"; id: string; name: string };

const sampleSpec = `openapi: 3.0.3
info:
  title: Demo Orders API
  version: 1.0.0
paths:
  /orders:
    get:
      parameters:
        - name: tenant
          in: query
          required: false
          schema:
            type: string
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
        "404":
          description: Empty tenant
          content:
            application/json:
              schema:
                type: object
                properties:
                  error:
                    type: string
    post:
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                title:
                  type: string
                paid:
                  type: boolean
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
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                title:
                  type: string
                paid:
                  type: boolean
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
    access: "Доступ",
    account: "Аккаунт",
    accountFound: "Аккаунт найден",
    accountNew: "Создание аккаунта",
    admin: "Админка",
    adminActive: "Активен",
    adminActor: "Пользователь",
    adminAudit: "Аудит платформы",
    adminDeleteUser: "Удалить пользователя",
    adminDisable: "Заблокировать",
    adminDisabled: "Заблокирован",
    adminEnable: "Разблокировать",
    adminEnabled: "Разблокирован",
    adminHealthy: "Платформа",
    adminInstances: "Все инстансы",
    adminLogs: "Все логи",
    adminProjects: "Все проекты",
    adminPromote: "Сделать админом",
    adminRevoke: "Снять админа",
    adminSearch: "Поиск в админке",
    adminSummary: "Сводка",
    adminUsers: "Пользователи",
    addMember: "Добавить участника",
    apiContract: "API-контракт",
    apiKey: "Mock API key",
    apiKeyHidden: "Ключ показывается один раз. Пересоздай его, если нужно скопировать снова.",
    audit: "Аудит",
    callMock: "Вызов mock",
    cancel: "Отмена",
    clear: "Очистить",
    confirmDelete: "Подтвердить удаление",
    confirmProjectName: "Введи название или никнейм",
    copied: "Скопировано",
    createProject: "Создать проект",
    dark: "Темная",
    delete: "Удалить",
    deleteInstance: "Удалить инстанс",
    deleteInstanceDetail: "Публичная ссылка перестанет работать, а логи инстанса будут удалены.",
    deleteMember: "Удалить участника",
    deleteMemberDetail: "Пользователь потеряет доступ к проекту.",
    deleteProject: "Удалить проект",
    deleteProjectDetail: "Будут удалены спецификации, инстансы, runtime state, логи и аудит проекта.",
    deleteSpec: "Удалить версию",
    deleteSpecDetail: "Версия, связанные инстансы и их логи будут удалены.",
    deleteWarning: "Это действие нельзя отменить.",
    description: "Описание",
    diagnostics: "Диагностика",
    email: "Email",
    emailOrUsername: "Email или никнейм",
    emptyAudit: "Событий аудита пока нет.",
    emptyLogs: "Запросов к выбранному инстансу пока нет.",
    emptyProjects: "Создай первый проект, чтобы загрузить OpenAPI.",
    emptyRuntime: "Runtime slots появятся после публикации инстансов.",
    emptySpecs: "Загрузи OpenAPI YAML или JSON, чтобы создать версию.",
    instance: "Инстанс",
    instances: "Инстансы",
    language: "Язык",
    light: "Светлая",
    login: "Войти",
    logout: "Выйти",
    logs: "Логи",
    matched: "совпало",
    memberEmail: "Email участника",
    memberUsername: "Никнейм участника",
    members: "Участники",
    mockBody: "Тело запроса",
    mockPath: "Путь",
    name: "Название",
    noProject: "Выбери проект",
    noUrl: "Полная ссылка показывается только после публикации или rotate token.",
    overview: "Обзор",
    password: "Пароль",
    pageNext: "Дальше",
    pagePrevious: "Назад",
    projectCreated: "Проект создан",
    projectDeleted: "Проект удален",
    projects: "Проекты",
    publish: "Опубликовать",
    publishMode: "Режим",
    refresh: "Обновить",
    register: "Зарегистрироваться",
    requireApiKey: "Требовать X-Mock-Api-Key",
    resetState: "Сбросить state",
    response: "Ответ",
    role: "Роль",
    rotateApiKey: "Новый API key",
    rotateToken: "Новая ссылка",
    routeCount: "маршрутов",
    runtime: "Runtime",
    runtimePlane: "Runtime plane",
    search: "Поиск",
    send: "Отправить",
    signedIn: "Вход выполнен",
    specDeleted: "Версия удалена",
    specName: "Имя спецификации",
    specUploadedInvalid: "Спецификация загружена с ошибками",
    specUploadedValid: "Спецификация проверена",
    specs: "Спецификации",
    state: "State",
    stateReset: "Runtime state сброшен",
    subtitle: "Консоль для приватных OpenAPI-проектов, warm Java slots, публичных mock-ссылок и диагностики.",
    continue: "Продолжить",
    heroTitle: "Mock Server as a Service для OpenAPI-команд",
    heroCopy: "Загрузите OpenAPI-спецификацию, получите рабочую ссылку на mock API и смотрите все запросы в одной консоли.",
    landingPrivate: "Приватные проекты",
    landingRuntime: "Warm Java slots",
    landingDiagnostics: "Логи и аудит",
    landingPrivateNote: "Доступ по ролям",
    landingRuntimeNote: "Быстрый запуск",
    landingDiagnosticsNote: "История запросов",
    landingNavFeatures: "Возможности",
    landingNavWorkflow: "Сценарий",
    landingNavSecurity: "Безопасность",
    landingHeroBadge: "Mock API для команд, которым нужно двигаться быстрее",
    landingHeroProofs: ["Работает по OpenAPI", "Доступ по ссылке", "Логи запросов в консоли"],
    landingPrimaryCta: "Начать бесплатно",
    landingSecondaryCta: "Войти",
    landingProofLabel: "Ключевые преимущества",
    landingMetrics: [
      { value: "< 1 мин", label: "от спецификации до рабочей ссылки" },
      { value: "1 ссылка", label: "для frontend, QA и внешней команды" },
      { value: "Все логи", label: "понятно, какой запрос пришел и что вернулось" },
      { value: "Роли", label: "проектом управляют только приглашенные участники" }
    ],
    landingProblemTitle: "Дайте команде API, не дожидаясь готового backend",
    landingProblemCopy: "MSaaS помогает быстро показать, протестировать и согласовать поведение API: загрузили спецификацию, получили ссылку, видите все запросы.",
    landingProblems: [
      { title: "Backend еще не готов", body: "Frontend и QA могут работать по контракту уже сейчас, не блокируя релизный цикл." },
      { title: "Локальные mock-и расходятся", body: "Один опубликованный instance дает всем одинаковое поведение, ссылку и наблюдаемость." },
      { title: "Непонятно, что сломалось", body: "Каждый запрос попадает в логи с matching-статусом, кодом ответа, задержкой и владельцем проекта." }
    ],
    landingFeaturesTitle: "Что получает команда",
    landingFeaturesCopy: "Минимум настройки, максимум пользы для разработки, тестирования и демонстрации интеграций.",
    landingFeatures: [
      { title: "Проекты по командам", body: "Разделяйте разные API и приглашайте участников только туда, где им нужен доступ." },
      { title: "Ссылка для тестирования", body: "Передавайте mock API frontend-разработчикам, QA или заказчику без ручных запусков." },
      { title: "Реалистичное поведение", body: "Проверяйте обычные ответы или CRUD-сценарии с состоянием." },
      { title: "Понятная диагностика", body: "Смотрите запросы, статусы, совпадения маршрутов и историю изменений." }
    ],
    landingWorkflowTitle: "От контракта до тестового API за один поток",
    landingWorkflowCopy: "Загрузка OpenAPI, валидация, публикация и диагностика остаются внутри одной консоли.",
    landingWorkflowSteps: [
      { title: "Загрузи OpenAPI", body: "YAML или JSON проходит нормализацию маршрутов, методов и примеров." },
      { title: "Опубликуй instance", body: "Warm Java slot подхватывает контракт без отдельного контейнера на каждый проект." },
      { title: "Отдай ссылку команде", body: "Запросы идут на /mock/{token}/..., а управление остается закрытым." },
      { title: "Смотри поведение", body: "Логи, state reset, token rotation и audit помогают быстро найти проблему." }
    ],
    landingSecurityTitle: "Контроль остается у владельца проекта",
    landingSecurityCopy: "Ссылку можно дать команде для вызовов API, но настройки, спецификации, состояние и логи остаются внутри консоли.",
    landingSecurityItems: [
      "Проект видят только участники",
      "Mock-ссылку можно пересоздать",
      "Логи и аудит доступны в консоли",
      "Админ видит состояние платформы"
    ],
    landingSceneProject: "orders-openapi.yaml",
    landingSceneRequest: "GET /mock/tk_8f2.../orders",
    landingSceneStatus: "200 matched",
    landingFinalTitle: "Начните с одной спецификации и сразу покажите работающий API",
    landingFinalCopy: "Создайте проект, загрузите OpenAPI и передайте команде ссылку, которую можно вызывать уже сегодня.",
    allProjects: "Все проекты",
    allProjectsOverview: "Обзор всех проектов",
    currentProject: "Открытый проект",
    createNewProject: "Новый проект",
    accountSettings: "Настройки аккаунта",
    profile: "Профиль",
    preferences: "Предпочтения",
    session: "Сессия",
    settings: "Настройки",
    chooseFile: "Выбрать файл",
    selectedFile: "Выбранный файл",
    save: "Сохранить",
    currentPassword: "Текущий пароль",
    newPassword: "Новый пароль",
    changeUsername: "Сменить никнейм",
    changePassword: "Сменить пароль",
    usernameUpdated: "Никнейм обновлен",
    passwordUpdated: "Пароль обновлен",
    passwordHint: "Введите пароль для найденного аккаунта.",
    registerHint: "Такого пользователя пока нет. Заполни email, никнейм и пароль, чтобы создать аккаунт.",
    back: "Назад",
    theme: "Тема",
    tokenPreview: "Token preview",
    tokenRotated: "Ссылка пересоздана",
    unmatched: "нет совпадения",
    uploadSpec: "Загрузить спецификацию",
    username: "Никнейм",
    viewerHint: "VIEWER может смотреть проект, но не изменять его.",
    workspace: "Workspace",
    workers: "Workers"
  },
  en: {
    access: "Access",
    account: "Account",
    accountFound: "Account found",
    accountNew: "Create account",
    admin: "Admin",
    adminActive: "Active",
    adminActor: "User",
    adminAudit: "Platform audit",
    adminDeleteUser: "Delete user",
    adminDisable: "Disable",
    adminDisabled: "Disabled",
    adminEnable: "Enable",
    adminEnabled: "Enabled",
    adminHealthy: "Platform",
    adminInstances: "All instances",
    adminLogs: "All logs",
    adminProjects: "All projects",
    adminPromote: "Make admin",
    adminRevoke: "Revoke admin",
    adminSearch: "Admin search",
    adminSummary: "Summary",
    adminUsers: "Users",
    addMember: "Add member",
    apiContract: "API contract",
    apiKey: "Mock API key",
    apiKeyHidden: "The key is shown once. Rotate it if you need to copy it again.",
    audit: "Audit",
    callMock: "Call mock",
    cancel: "Cancel",
    clear: "Clear",
    confirmDelete: "Confirm delete",
    confirmProjectName: "Type the name or username",
    copied: "Copied",
    createProject: "Create project",
    dark: "Dark",
    delete: "Delete",
    deleteInstance: "Delete instance",
    deleteInstanceDetail: "The public link will stop working and instance logs will be deleted.",
    deleteMember: "Remove member",
    deleteMemberDetail: "The user will lose access to this project.",
    deleteProject: "Delete project",
    deleteProjectDetail: "Specs, instances, runtime state, logs, and audit events will be deleted.",
    deleteSpec: "Delete version",
    deleteSpecDetail: "This version, related instances, and their logs will be deleted.",
    deleteWarning: "This action cannot be undone.",
    description: "Description",
    diagnostics: "Diagnostics",
    email: "Email",
    emailOrUsername: "Email or username",
    emptyAudit: "No audit events yet.",
    emptyLogs: "No requests for this instance yet.",
    emptyProjects: "Create the first project to upload OpenAPI.",
    emptyRuntime: "Runtime slots appear after publishing instances.",
    emptySpecs: "Upload OpenAPI YAML or JSON to create a version.",
    instance: "Instance",
    instances: "Instances",
    language: "Language",
    light: "Light",
    login: "Login",
    logout: "Logout",
    logs: "Logs",
    matched: "matched",
    memberEmail: "Member email",
    memberUsername: "Member username",
    members: "Members",
    mockBody: "Request body",
    mockPath: "Path",
    name: "Name",
    noProject: "Select a project",
    noUrl: "The full link is shown only after publish or rotate token.",
    overview: "Overview",
    password: "Password",
    pageNext: "Next",
    pagePrevious: "Previous",
    projectCreated: "Project created",
    projectDeleted: "Project deleted",
    projects: "Projects",
    publish: "Publish",
    publishMode: "Mode",
    refresh: "Refresh",
    register: "Register",
    requireApiKey: "Require X-Mock-Api-Key",
    resetState: "Reset state",
    response: "Response",
    role: "Role",
    rotateApiKey: "New API key",
    rotateToken: "Rotate link",
    routeCount: "routes",
    runtime: "Runtime",
    runtimePlane: "Runtime plane",
    search: "Search",
    send: "Send",
    signedIn: "Signed in",
    specDeleted: "Version deleted",
    specName: "Specification name",
    specUploadedInvalid: "Specification uploaded with errors",
    specUploadedValid: "Specification validated",
    specs: "Specifications",
    state: "State",
    stateReset: "Runtime state reset",
    subtitle: "Console for private OpenAPI projects, warm Java slots, public mock links, and diagnostics.",
    continue: "Continue",
    heroTitle: "Mock Server as a Service for OpenAPI teams",
    heroCopy: "Upload an OpenAPI specification, get a working mock API link, and inspect every request in one console.",
    landingPrivate: "Private projects",
    landingRuntime: "Warm Java slots",
    landingDiagnostics: "Logs and audit",
    landingPrivateNote: "Role-based access",
    landingRuntimeNote: "Fast startup",
    landingDiagnosticsNote: "Request history",
    landingNavFeatures: "Features",
    landingNavWorkflow: "Workflow",
    landingNavSecurity: "Security",
    landingHeroBadge: "Mock API for teams that need to move faster",
    landingHeroProofs: ["Runs from OpenAPI", "Share by link", "Request logs in console"],
    landingPrimaryCta: "Start free",
    landingSecondaryCta: "Open login",
    landingProofLabel: "Key benefits",
    landingMetrics: [
      { value: "< 1 min", label: "from specification to a working link" },
      { value: "1 link", label: "for frontend, QA, and external teams" },
      { value: "All logs", label: "see every request and response clearly" },
      { value: "Roles", label: "only invited members manage projects" }
    ],
    landingProblemTitle: "Give your team an API before the backend is ready",
    landingProblemCopy: "MSaaS helps you show, test, and agree on API behavior quickly: upload a specification, get a link, and inspect every request.",
    landingProblems: [
      { title: "Backend is not ready", body: "Frontend and QA can work against the contract now without blocking the release cycle." },
      { title: "Local mocks drift apart", body: "One published instance gives everyone the same behavior, link, and observability." },
      { title: "Failures are hard to explain", body: "Every request is logged with matching status, response code, latency, and project owner." }
    ],
    landingFeaturesTitle: "What the team gets",
    landingFeaturesCopy: "Minimal setup, practical value for development, QA, and integration demos.",
    landingFeatures: [
      { title: "Team projects", body: "Separate APIs by project and invite people only where they need access." },
      { title: "Testing link", body: "Share mock APIs with frontend, QA, or clients without manual local setup." },
      { title: "Realistic behavior", body: "Validate simple responses or CRUD flows with state." },
      { title: "Clear diagnostics", body: "Inspect requests, statuses, route matches, and change history." }
    ],
    landingWorkflowTitle: "From contract to test API in one flow",
    landingWorkflowCopy: "OpenAPI upload, validation, publishing, and diagnostics stay inside one console.",
    landingWorkflowSteps: [
      { title: "Upload OpenAPI", body: "YAML or JSON is normalized into routes, methods, and examples." },
      { title: "Publish an instance", body: "A warm Java slot loads the contract without a separate container per project." },
      { title: "Share the link", body: "Requests go to /mock/{token}/..., while management stays private." },
      { title: "Inspect behavior", body: "Logs, state reset, token rotation, and audit make issues easy to trace." }
    ],
    landingSecurityTitle: "Control stays with the project owner",
    landingSecurityCopy: "You can share the API link for calls, while settings, specifications, state, and logs stay inside the console.",
    landingSecurityItems: [
      "Only members can view the project",
      "Mock links can be rotated",
      "Logs and audit stay in the console",
      "Admins can inspect platform health"
    ],
    landingSceneProject: "orders-openapi.yaml",
    landingSceneRequest: "GET /mock/tk_8f2.../orders",
    landingSceneStatus: "200 matched",
    landingFinalTitle: "Start with one specification and show a working API today",
    landingFinalCopy: "Create a project, upload OpenAPI, and share a link your team can call immediately.",
    allProjects: "All projects",
    allProjectsOverview: "All projects overview",
    currentProject: "Open project",
    createNewProject: "New project",
    accountSettings: "Account settings",
    profile: "Profile",
    preferences: "Preferences",
    session: "Session",
    settings: "Settings",
    chooseFile: "Choose file",
    selectedFile: "Selected file",
    save: "Save",
    currentPassword: "Current password",
    newPassword: "New password",
    changeUsername: "Change username",
    changePassword: "Change password",
    usernameUpdated: "Username updated",
    passwordUpdated: "Password updated",
    passwordHint: "Enter the password for the account we found.",
    registerHint: "No user exists yet. Add email, username, and password to create an account.",
    back: "Back",
    theme: "Theme",
    tokenPreview: "Token preview",
    tokenRotated: "Link rotated",
    unmatched: "unmatched",
    uploadSpec: "Upload specification",
    username: "Username",
    viewerHint: "VIEWER can inspect the project, but cannot mutate it.",
    workspace: "Workspace",
    workers: "Workers"
  }
} as const;

const views: Array<{ key: ViewKey; icon: React.ReactNode }> = [
  { key: "overview", icon: <Activity size={16} /> },
  { key: "specs", icon: <FileJson size={16} /> },
  { key: "runtime", icon: <Server size={16} /> },
  { key: "logs", icon: <ListFilter size={16} /> },
  { key: "access", icon: <Users size={16} /> },
  { key: "settings", icon: <Settings2 size={16} /> }
];

const viewRoutes: Record<ViewKey, string> = {
  overview: "/console/overview",
  specs: "/console/specifications",
  runtime: "/console/runtime",
  logs: "/console/logs",
  access: "/console/access",
  settings: "/console/settings",
  admin: "/console/admin"
};

const routeViews = Object.fromEntries(Object.entries(viewRoutes).map(([key, path]) => [path, key])) as Record<string, ViewKey>;
const projectViewSegments: Partial<Record<ViewKey, string>> = {
  overview: "overview",
  specs: "specifications",
  runtime: "runtime",
  logs: "logs",
  access: "access"
};
const segmentViews = Object.fromEntries(Object.entries(projectViewSegments).map(([key, segment]) => [segment, key])) as Record<string, ViewKey>;

type AppRoute = {
  view: ViewKey | null;
  publicView: PublicView;
  authStep: AuthStep;
  projectSlug?: string;
};

function parseAppRoute(pathname: string): AppRoute {
  const normalized = pathname.replace(/\/+$/, "") || "/";
  const projectRoute = normalized.match(/^\/console\/([^/]+)\/([^/]+)$/);
  if (projectRoute) {
    const projectView = segmentViews[projectRoute[2]];
    if (projectView) {
      return {
        view: projectView,
        publicView: "auth",
        authStep: "identifier",
        projectSlug: safeDecode(projectRoute[1])
      };
    }
  }
  const routeView = routeViews[normalized];
  if (routeView) {
    return { view: routeView, publicView: "auth", authStep: "identifier" };
  }
  if (normalized === "/register") {
    return { view: null, publicView: "auth", authStep: "register" };
  }
  if (normalized === "/login") {
    return { view: null, publicView: "auth", authStep: "identifier" };
  }
  return { view: null, publicView: "landing", authStep: "identifier" };
}

function writeRoute(path: string, replace = false) {
  if (window.location.pathname === path) return;
  const method = replace ? "replaceState" : "pushState";
  window.history[method](null, "", path);
}

function safeDecode(value: string) {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function projectRouteSegment(project: Pick<Project, "id" | "name">) {
  const readable = project.name.trim().replace(/\s+/g, "-") || project.id;
  return encodeURIComponent(readable);
}

function normalizeProjectSlug(value: string) {
  return value.trim().toLowerCase().replace(/[-_\s]+/g, "");
}

function findProjectBySlug(projects: Project[], slug: string) {
  const normalizedSlug = normalizeProjectSlug(safeDecode(slug));
  return projects.find((project) =>
    normalizeProjectSlug(project.name) === normalizedSlug ||
    normalizeProjectSlug(project.id) === normalizedSlug
  ) ?? null;
}

function isProjectView(view: ViewKey) {
  return Boolean(projectViewSegments[view]);
}

function App() {
  const [routeSeed] = useState(() => parseAppRoute(window.location.pathname));
  const [theme, setTheme] = useState<Theme>(() => (localStorage.getItem("msaas.theme") as Theme) || "light");
  const [lang, setLang] = useState<Lang>(() => (localStorage.getItem("msaas.lang") as Lang) || "ru");
  const [view, setView] = useState<ViewKey>(routeSeed.view ?? "overview");
  const [publicView, setPublicView] = useState<PublicView>(routeSeed.view ? "auth" : routeSeed.publicView);
  const [authStep, setAuthStep] = useState<AuthStep>(routeSeed.authStep);
  const [token, setToken] = useState(() => localStorage.getItem("msaas.token") ?? "");
  const [user, setUser] = useState<User | null>(() => readStoredUser());
  const [authIdentifier, setAuthIdentifier] = useState("demo@example.com");
  const [email, setEmail] = useState("demo@example.com");
  const [username, setUsername] = useState("demo");
  const [password, setPassword] = useState("password");
  const [accountUsername, setAccountUsername] = useState(() => readStoredUser()?.username ?? "");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [pendingProjectSlug, setPendingProjectSlug] = useState(routeSeed.projectSlug ?? "");
  const [projectMemberIds, setProjectMemberIds] = useState<string[]>([]);
  const [projectLogCount, setProjectLogCount] = useState(0);
  const [projectName, setProjectName] = useState("Demo API");
  const [projectDescription, setProjectDescription] = useState("OpenAPI-backed mock project");
  const [projectFilter, setProjectFilter] = useState("");
  const [projectCreateOpen, setProjectCreateOpen] = useState(false);
  const [projectPage, setProjectPage] = useState(0);
  const [specName, setSpecName] = useState("orders-openapi.yaml");
  const [specSource, setSpecSource] = useState(sampleSpec);
  const [specPage, setSpecPage] = useState(0);
  const [specVersions, setSpecVersions] = useState<SpecVersion[]>([]);
  const [routeExplorerVersionId, setRouteExplorerVersionId] = useState("");
  const [contractRoutes, setContractRoutes] = useState<ContractRoute[]>([]);
  const [previewRouteKey, setPreviewRouteKey] = useState("");
  const [previewStatusCode, setPreviewStatusCode] = useState(200);
  const [previewContentType, setPreviewContentType] = useState("application/json");
  const [previewExampleName, setPreviewExampleName] = useState("");
  const [previewSeed, setPreviewSeed] = useState("");
  const [responsePreview, setResponsePreview] = useState<ResponsePreview | null>(null);
  const [instances, setInstances] = useState<MockInstance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState("");
  const [instancePage, setInstancePage] = useState(0);
  const [publishMode, setPublishMode] = useState<InstanceMode>("STATEFUL");
  const [requireApiKey, setRequireApiKey] = useState(false);
  const [issuedUrls, setIssuedUrls] = useState<Record<string, string>>({});
  const [issuedApiKeys, setIssuedApiKeys] = useState<Record<string, string>>({});
  const [mockApiKeyInput, setMockApiKeyInput] = useState("");
  const [mockMethod, setMockMethod] = useState("GET");
  const [mockPath, setMockPath] = useState("/orders?__status=200");
  const [mockBody, setMockBody] = useState('{"title":"Test order","paid":false}');
  const [mockResponse, setMockResponse] = useState("");
  const [rateLimitEnabled, setRateLimitEnabled] = useState(true);
  const [rateLimitRequests, setRateLimitRequests] = useState(120);
  const [rateLimitWindowSeconds, setRateLimitWindowSeconds] = useState(60);
  const [smartResponsesEnabled, setSmartResponsesEnabled] = useState(true);
  const [smartSeedMode, setSmartSeedMode] = useState("STABLE");
  const [scenarios, setScenarios] = useState<MockScenario[]>([]);
  const [responseRules, setResponseRules] = useState<ResponseRule[]>([]);
  const [profiles, setProfiles] = useState<MockProfile[]>([]);
  const [activeProfile, setActiveProfile] = useState("dev");
  const [faultProfileEnabled, setFaultProfileEnabled] = useState(false);
  const [faultErrorRate, setFaultErrorRate] = useState(0);
  const [faultStatusCode, setFaultStatusCode] = useState(500);
  const [latencyMinMs, setLatencyMinMs] = useState(0);
  const [latencyMaxMs, setLatencyMaxMs] = useState(0);
  const [editingProfileId, setEditingProfileId] = useState("");
  const [profileName, setProfileName] = useState("qa-fast-fail");
  const [profileFaultEnabled, setProfileFaultEnabled] = useState(true);
  const [profileErrorRate, setProfileErrorRate] = useState(10);
  const [profileStatusCode, setProfileStatusCode] = useState(500);
  const [profileLatencyMin, setProfileLatencyMin] = useState(120);
  const [profileLatencyMax, setProfileLatencyMax] = useState(450);
  const [editingRuleId, setEditingRuleId] = useState("");
  const [ruleName, setRuleName] = useState("VIP customer email");
  const [ruleEnabled, setRuleEnabled] = useState(true);
  const [rulePriority, setRulePriority] = useState(100);
  const [ruleOperationId, setRuleOperationId] = useState("");
  const [ruleMethod, setRuleMethod] = useState("GET");
  const [rulePathTemplate, setRulePathTemplate] = useState("/orders");
  const [ruleFieldPath, setRuleFieldPath] = useState("customer.email");
  const [ruleType, setRuleType] = useState("FIXED");
  const [ruleFixedValue, setRuleFixedValue] = useState("\"vip@example.com\"");
  const [ruleMinValue, setRuleMinValue] = useState(1);
  const [ruleMaxValue, setRuleMaxValue] = useState(100);
  const [ruleEnumValues, setRuleEnumValues] = useState("pending, paid, cancelled");
  const [ruleTemplate, setRuleTemplate] = useState("{{query.name}}");
  const [editingScenarioId, setEditingScenarioId] = useState("");
  const [scenarioName, setScenarioName] = useState("Slow 500 example");
  const [scenarioEnabled, setScenarioEnabled] = useState(true);
  const [scenarioPriority, setScenarioPriority] = useState(100);
  const [scenarioOperationId, setScenarioOperationId] = useState("");
  const [scenarioMethod, setScenarioMethod] = useState("GET");
  const [scenarioPathTemplate, setScenarioPathTemplate] = useState("/orders");
  const [scenarioStatusCode, setScenarioStatusCode] = useState(200);
  const [scenarioContentType, setScenarioContentType] = useState("application/json");
  const [scenarioDelayMs, setScenarioDelayMs] = useState(0);
  const [scenarioBody, setScenarioBody] = useState('{"id":"{{path.id}}","request":"{{uuid}}","createdAt":"{{now}}"}');
  const [logs, setLogs] = useState<RequestLog[]>([]);
  const [logMethodFilter, setLogMethodFilter] = useState("ALL");
  const [logMatchFilter, setLogMatchFilter] = useState("ALL");
  const [logSourceFilter, setLogSourceFilter] = useState("ALL");
  const [logStatusFilter, setLogStatusFilter] = useState("ALL");
  const [logPage, setLogPage] = useState(0);
  const [selectedLog, setSelectedLog] = useState<RequestLog | null>(null);
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [memberPage, setMemberPage] = useState(0);
  const [memberEmail, setMemberEmail] = useState("");
  const [memberRole, setMemberRole] = useState<ProjectRole>("VIEWER");
  const [audit, setAudit] = useState<AuditEvent[]>([]);
  const [projectAuditPage, setProjectAuditPage] = useState(0);
  const [runtimeWorkers, setRuntimeWorkers] = useState<RuntimeWorker[]>([]);
  const [runtimeSlots, setRuntimeSlots] = useState<RuntimeSlot[]>([]);
  const [runtimeSlotPage, setRuntimeSlotPage] = useState(0);
  const [stateSnapshot, setStateSnapshot] = useState<Record<string, unknown>>({});
  const [adminSummary, setAdminSummary] = useState<AdminSummary | null>(null);
  const [adminUsers, setAdminUsers] = useState<AdminUser[]>([]);
  const [adminProjects, setAdminProjects] = useState<AdminProject[]>([]);
  const [adminInstances, setAdminInstances] = useState<AdminInstance[]>([]);
  const [adminLogs, setAdminLogs] = useState<AdminLog[]>([]);
  const [adminAudit, setAdminAudit] = useState<AdminAudit[]>([]);
  const [adminWorkers, setAdminWorkers] = useState<RuntimeWorker[]>([]);
  const [adminSlots, setAdminSlots] = useState<RuntimeSlot[]>([]);
  const [adminQuery, setAdminQuery] = useState("");
  const [adminUserPage, setAdminUserPage] = useState(0);
  const [adminProjectPage, setAdminProjectPage] = useState(0);
  const [adminInstancePage, setAdminInstancePage] = useState(0);
  const [adminSlotPage, setAdminSlotPage] = useState(0);
  const [adminLogUserId, setAdminLogUserId] = useState("ALL");
  const [adminLogPage, setAdminLogPage] = useState(0);
  const [adminLogTotal, setAdminLogTotal] = useState(0);
  const [adminLogTotalPages, setAdminLogTotalPages] = useState(0);
  const [adminAuditActorId, setAdminAuditActorId] = useState("ALL");
  const [adminAuditPage, setAdminAuditPage] = useState(0);
  const [adminAuditTotal, setAdminAuditTotal] = useState(0);
  const [adminAuditTotalPages, setAdminAuditTotalPages] = useState(0);
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const [busy, setBusy] = useState<Record<string, boolean>>({});
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  const [deleteConfirmName, setDeleteConfirmName] = useState("");

  const t = text[lang];
  const selectedProject = projects.find((project) => project.id === selectedProjectId) ?? null;
  const selectedInstance = instances.find((instance) => instance.id === selectedInstanceId) ?? null;
  const selectedPreviewRoute = contractRoutes.find((route) => routeKey(route) === previewRouteKey) ?? contractRoutes[0] ?? null;
  const isAdmin = user?.systemRole === "ADMIN";
  const visibleViews = views;
  const canWrite = selectedProject?.role === "OWNER" || selectedProject?.role === "MEMBER";
  const canOwn = selectedProject?.role === "OWNER";
  const validSpecs = specVersions.filter((version) => version.status === "VALID").length;
  const runningInstances = instances.filter((instance) => instance.status === "RUNNING").length;
  const filteredProjects = projects.filter((project) =>
    `${project.name} ${project.description}`.toLowerCase().includes(projectFilter.toLowerCase())
  );
  const visibleLogs = logs.filter((log) => {
    const methodOk = logMethodFilter === "ALL" || log.method === logMethodFilter;
    const matchOk = logMatchFilter === "ALL" || (logMatchFilter === "MATCHED" ? log.matched : !log.matched);
    const sourceOk = logSourceFilter === "ALL" || log.responseSource === logSourceFilter;
    const statusOk = logStatusFilter === "ALL"
      || (logStatusFilter === "2xx" && log.responseStatus >= 200 && log.responseStatus < 300)
      || (logStatusFilter === "4xx" && log.responseStatus >= 400 && log.responseStatus < 500)
      || (logStatusFilter === "5xx" && log.responseStatus >= 500 && log.responseStatus < 600);
    return methodOk && matchOk && sourceOk && statusOk;
  });
  const projectListPage = paginate(filteredProjects, projectPage, 5);
  const visibleLogsPage = paginate(visibleLogs, logPage, 100);
  const memberListPage = paginate(members, memberPage, 7);
  const projectAuditListPage = paginate(audit, projectAuditPage, 100);
  const adminUserListPage = paginate(adminUsers, adminUserPage, 8);
  const adminProjectListPage = paginate(adminProjects, adminProjectPage, 50);
  const adminInstanceListPage = paginate(adminInstances, adminInstancePage, 50);
  const adminSlotListPage = paginate(adminSlots, adminSlotPage, 8);
  const allProjectMembers = projectMemberIds.length || projects.reduce((sum, project) => sum + project.memberCount, 0);
  const adminPageSize = 100;

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
    const onPopState = () => {
      const route = parseAppRoute(window.location.pathname);
      if (route.view) {
        if (!localStorage.getItem("msaas.token")) {
          setPublicView("auth");
          setAuthStep("identifier");
          writeRoute("/login", true);
          return;
        }
        setView(route.view);
        if (route.projectSlug) {
          setPendingProjectSlug(route.projectSlug);
          const routedProject = findProjectBySlug(projects, route.projectSlug);
          if (routedProject) {
            setSelectedProjectId(routedProject.id);
          }
        } else {
          setPendingProjectSlug("");
        }
        return;
      }
      setPublicView(route.publicView);
      setAuthStep(route.authStep);
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, [projects]);

  useEffect(() => {
    if (!toast) return;
    const timeout = window.setTimeout(() => setToast(null), 5200);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  useEffect(() => {
    if (token) {
      const route = parseAppRoute(window.location.pathname);
      if (!route.view) {
        writeRoute(viewRoutes[view], true);
      } else if (route.projectSlug) {
        setPendingProjectSlug(route.projectSlug);
      }
      void refreshProjects();
    } else if (parseAppRoute(window.location.pathname).view) {
      setPublicView("auth");
      setAuthStep("identifier");
      writeRoute("/login", true);
    }
  }, [token]);

  useEffect(() => {
    if (selectedProjectId) {
      const routedProject = projects.find((project) => project.id === selectedProjectId);
      if (routedProject && isProjectView(view)) {
        writeRoute(pathForView(view, routedProject), true);
      }
      void refreshProjectDetails(selectedProjectId);
      setSpecPage(0);
      setInstancePage(0);
      setLogPage(0);
      setMemberPage(0);
      setProjectAuditPage(0);
      setRuntimeSlotPage(0);
    }
  }, [selectedProjectId]);

  useEffect(() => {
    setProjectPage(0);
  }, [projectFilter]);

  useEffect(() => {
    setLogPage(0);
  }, [logMethodFilter, logMatchFilter, logSourceFilter, logStatusFilter, selectedInstanceId]);

  useEffect(() => {
    setAdminUserPage(0);
  }, [adminQuery]);

  useEffect(() => {
    if (selectedInstance) {
      setMockApiKeyInput(issuedApiKeys[selectedInstance.id] ?? "");
      setRateLimitEnabled(selectedInstance.rateLimitEnabled);
      setRateLimitRequests(selectedInstance.rateLimitRequests);
      setRateLimitWindowSeconds(selectedInstance.rateLimitWindowSeconds);
      setSmartResponsesEnabled(selectedInstance.smartResponsesEnabled);
      setSmartSeedMode(selectedInstance.smartSeedMode || "STABLE");
      setActiveProfile(selectedInstance.activeProfile || "dev");
      setFaultProfileEnabled(selectedInstance.faultProfileEnabled);
      setFaultErrorRate(selectedInstance.faultErrorRate);
      setFaultStatusCode(selectedInstance.faultStatusCode);
      setLatencyMinMs(selectedInstance.latencyMinMs);
      setLatencyMaxMs(selectedInstance.latencyMaxMs);
      void refreshScenarios(selectedInstance.id);
      void refreshResponseRules(selectedInstance.id);
      void refreshProfiles(selectedInstance.id);
      void refreshLogs(selectedInstance.id);
      void refreshState(selectedInstance.id);
    } else {
      setScenarios([]);
      setResponseRules([]);
      setProfiles([]);
    }
  }, [selectedInstanceId, issuedApiKeys]);

  useEffect(() => {
    if (specVersions.length === 0) {
      setRouteExplorerVersionId("");
      return;
    }
    if (!routeExplorerVersionId || !specVersions.some((version) => version.id === routeExplorerVersionId)) {
      setRouteExplorerVersionId(specVersions[0].id);
    }
  }, [specVersions, routeExplorerVersionId]);

  useEffect(() => {
    if (routeExplorerVersionId) {
      void refreshContractRoutes(routeExplorerVersionId);
    } else {
      setContractRoutes([]);
    }
  }, [routeExplorerVersionId]);

  useEffect(() => {
    if (contractRoutes.length === 0) {
      setPreviewRouteKey("");
      setResponsePreview(null);
      return;
    }
    const nextRoute = contractRoutes.find((route) => routeKey(route) === previewRouteKey) ?? contractRoutes[0];
    if (routeKey(nextRoute) !== previewRouteKey) {
      setPreviewRouteKey(routeKey(nextRoute));
    }
    const firstResponse = nextRoute.responses[0];
    if (firstResponse) {
      setPreviewStatusCode(firstResponse.statusCode);
      setPreviewContentType(firstResponse.contentType);
      setPreviewExampleName("");
    }
  }, [contractRoutes, previewRouteKey]);

  useEffect(() => {
    setAccountUsername(user?.username ?? "");
  }, [user?.username]);

  useEffect(() => {
    if (view === "admin" && !isAdmin) {
      navigateView("overview", true);
    }
    if (view === "admin" && isAdmin) {
      void refreshAdmin();
    }
  }, [view, isAdmin]);

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

  async function run(key: string, action: () => Promise<void>) {
    setBusy((current) => ({ ...current, [key]: true }));
    try {
      await action();
    } catch (error) {
      showError(error);
    } finally {
      setBusy((current) => ({ ...current, [key]: false }));
    }
  }

  async function lookupAccount() {
    const identifier = authIdentifier.trim();
    if (!identifier) {
      notify(t.emailOrUsername);
      return;
    }
    await run("auth-lookup", async () => {
      const data = await api<AuthLookup>("/api/auth/lookup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ identifier })
      });
      setToast(null);
      if (data.exists) {
        setAuthStep("password");
        writeRoute("/login", true);
        if (data.username) {
          setUsername(data.username);
        }
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

  async function auth(path: "/api/auth/login" | "/api/auth/register") {
    await run("auth", async () => {
      const body = path === "/api/auth/register"
        ? { email, username, password }
        : { identifier: authIdentifier, password };
      const data = await api<AuthResponse>(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      applyAuthResponse(data);
      setAuthStep("identifier");
      navigateView("overview", true);
      notify(`${t.signedIn}: ${displayUser(data.user)}`);
    });
  }

  function applyAuthResponse(data: AuthResponse) {
    localStorage.setItem("msaas.token", data.token);
    localStorage.setItem("msaas.user", JSON.stringify(data.user));
    setToken(data.token);
    setUser(data.user);
  }

  async function updateAccountUsername() {
    await run("account-username", async () => {
      const data = await api<AuthResponse>("/api/account/username", {
        method: "PATCH",
        headers: authHeaders,
        body: JSON.stringify({ username: accountUsername })
      });
      applyAuthResponse(data);
      notify(t.usernameUpdated);
    });
  }

  async function updateAccountPassword() {
    await run("account-password", async () => {
      const data = await api<AuthResponse>("/api/account/password", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ currentPassword, newPassword })
      });
      applyAuthResponse(data);
      setCurrentPassword("");
      setNewPassword("");
      notify(t.passwordUpdated);
    });
  }

  async function refreshProjects() {
    await run("projects", async () => {
      const data = await api<Project[]>("/api/projects", { headers: authHeaders });
      const memberLists = await Promise.all(data.map(async (project) => {
        try {
          return await api<ProjectMember[]>(`/api/projects/${project.id}/members`, { headers: authHeaders });
        } catch {
          return [];
        }
      }));
      const uniqueMembers = new Set(memberLists.flat().map((member) => member.userId).filter(Boolean));
      setProjects(data);
      setProjectMemberIds([...uniqueMembers]);

      const route = parseAppRoute(window.location.pathname);
      const routeSlug = route.projectSlug ?? pendingProjectSlug;
      const routedProject = routeSlug ? findProjectBySlug(data, routeSlug) : null;
      const existingProject = data.find((project) => project.id === selectedProjectId) ?? null;
      const nextProject = routedProject ?? existingProject ?? data[0] ?? null;

      if (routedProject) {
        setPendingProjectSlug("");
      }
      setSelectedProjectId(nextProject?.id ?? "");
      if (nextProject && route.view && isProjectView(route.view)) {
        writeRoute(pathForView(route.view, nextProject), true);
      }
    });
  }

  async function createProject() {
    await run("create-project", async () => {
      const project = await api<Project>("/api/projects", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ name: projectName, description: projectDescription })
      });
      setProjects((current) => [project, ...current]);
      setSelectedProjectId(project.id);
      setProjectCreateOpen(false);
      writeRoute(pathForView("overview", project));
      notify(t.projectCreated);
    });
  }

  async function refreshProjectDetails(projectId: string) {
    await run("details", async () => {
      const [versions, projectInstances, projectMembers, projectAudit, workers, slots] = await Promise.all([
        api<SpecVersion[]>(`/api/projects/${projectId}/spec-versions`, { headers: authHeaders }),
        api<MockInstance[]>(`/api/projects/${projectId}/instances`, { headers: authHeaders }),
        api<ProjectMember[]>(`/api/projects/${projectId}/members`, { headers: authHeaders }),
        api<AuditEvent[]>(`/api/projects/${projectId}/audit?limit=500`, { headers: authHeaders }),
        api<RuntimeWorker[]>("/api/runtime/workers", { headers: authHeaders }),
        api<RuntimeSlot[]>("/api/runtime/slots", { headers: authHeaders })
      ]);
      const projectLogEntries = await Promise.all(projectInstances.map(async (instance) => {
        try {
          const instanceLogs = await api<RequestLog[]>(`/api/instances/${instance.id}/logs?limit=500`, { headers: authHeaders });
          return [instance.id, instanceLogs] as const;
        } catch {
          return [instance.id, [] as RequestLog[]] as const;
        }
      }));
      const logMap = new Map(projectLogEntries);
      const nextSelectedInstanceId = projectInstances.some((instance) => instance.id === selectedInstanceId)
        ? selectedInstanceId
        : projectInstances[0]?.id ?? "";
      setSpecVersions(versions);
      setSpecSource(versions[0]?.source || sampleSpec);
      setSpecName(versions[0]?.name || "orders-openapi.yaml");
      setInstances(projectInstances);
      setMembers(projectMembers);
      setAudit(projectAudit);
      setRuntimeWorkers(workers);
      setRuntimeSlots(slots);
      setProjectLogCount(projectLogEntries.reduce((sum, [, instanceLogs]) => sum + instanceLogs.length, 0));
      setSelectedInstanceId(nextSelectedInstanceId);
      setLogs(nextSelectedInstanceId ? logMap.get(nextSelectedInstanceId) ?? [] : []);
      setSelectedLog(null);
    });
  }

  async function refreshRuntimePlane() {
    await run("runtime-plane", async () => {
      const [workers, slots] = await Promise.all([
        api<RuntimeWorker[]>("/api/runtime/workers", { headers: authHeaders }),
        api<RuntimeSlot[]>("/api/runtime/slots", { headers: authHeaders })
      ]);
      setRuntimeWorkers(workers);
      setRuntimeSlots(slots);
    });
  }

  async function refreshAdmin(options: Partial<{ logPage: number; auditPage: number; logUserId: string; auditActorId: string }> = {}) {
    if (!isAdmin) return;
    await run("admin", async () => {
      const query = encodeURIComponent(adminQuery.trim());
      const suffix = query ? `?query=${query}` : "";
      const logPage = options.logPage ?? adminLogPage;
      const auditPage = options.auditPage ?? adminAuditPage;
      const logUserId = options.logUserId ?? adminLogUserId;
      const auditActorId = options.auditActorId ?? adminAuditActorId;
      const logUserSuffix = logUserId === "ALL" ? "" : `&userId=${encodeURIComponent(logUserId)}`;
      const auditActorSuffix = auditActorId === "ALL" ? "" : `&actorId=${encodeURIComponent(auditActorId)}`;
      const [summary, users, projects, platformInstances, platformLogs, platformAudit, workers, slots] = await Promise.all([
        api<AdminSummary>("/api/admin/summary", { headers: authHeaders }),
        api<AdminUser[]>(`/api/admin/users${suffix}`, { headers: authHeaders }),
        api<AdminProject[]>(`/api/admin/projects${suffix}`, { headers: authHeaders }),
        api<AdminInstance[]>("/api/admin/instances?limit=1000", { headers: authHeaders }),
        api<AdminPage<AdminLog>>(`/api/admin/logs?page=${logPage}&size=${adminPageSize}${logUserSuffix}`, { headers: authHeaders }),
        api<AdminPage<AdminAudit>>(`/api/admin/audit?page=${auditPage}&size=${adminPageSize}${auditActorSuffix}`, { headers: authHeaders }),
        api<RuntimeWorker[]>("/api/admin/runtime/workers", { headers: authHeaders }),
        api<RuntimeSlot[]>("/api/admin/runtime/slots", { headers: authHeaders })
      ]);
      setAdminSummary(summary);
      setAdminUsers(users);
      setAdminProjects(projects);
      setAdminInstances(platformInstances);
      setAdminLogs(platformLogs.items);
      setAdminLogPage(platformLogs.page);
      setAdminLogTotal(platformLogs.totalElements);
      setAdminLogTotalPages(platformLogs.totalPages);
      setAdminAudit(platformAudit.items);
      setAdminAuditPage(platformAudit.page);
      setAdminAuditTotal(platformAudit.totalElements);
      setAdminAuditTotalPages(platformAudit.totalPages);
      setAdminWorkers(workers);
      setAdminSlots(slots);
    });
  }

  async function setUserDisabled(target: AdminUser, disabled: boolean) {
    await run(`admin-user-${target.id}`, async () => {
      const updated = await api<AdminUser>(`/api/admin/users/${target.id}/${disabled ? "disable" : "enable"}`, {
        method: "POST",
        headers: authHeaders
      });
      setAdminUsers((current) => current.map((userItem) => (userItem.id === updated.id ? updated : userItem)));
      notify(disabled ? t.adminDisabled : t.adminEnabled);
    });
  }

  async function setUserAdmin(target: AdminUser, makeAdmin: boolean) {
    await run(`admin-role-${target.id}`, async () => {
      const updated = await api<AdminUser>(`/api/admin/users/${target.id}/${makeAdmin ? "make-admin" : "revoke-admin"}`, {
        method: "POST",
        headers: authHeaders
      });
      setAdminUsers((current) => current.map((userItem) => (userItem.id === updated.id ? updated : userItem)));
      notify(makeAdmin ? t.adminPromote : t.adminRevoke);
    });
  }

  async function deleteAdminUser(target: AdminUser) {
    await run(`admin-delete-user-${target.id}`, async () => {
      await api<void>(`/api/admin/users/${target.id}`, {
        method: "DELETE",
        headers: authHeaders,
        body: JSON.stringify({ confirmName: deleteConfirmName })
      });
      setAdminUsers((current) => current.filter((userItem) => userItem.id !== target.id));
      closeDeleteDialog();
      await refreshAdmin({ logPage: 0, auditPage: 0 });
    });
  }

  async function deleteAdminProject(target: AdminProject) {
    await run(`admin-delete-project-${target.id}`, async () => {
      await api<void>(`/api/admin/projects/${target.id}`, {
        method: "DELETE",
        headers: authHeaders,
        body: JSON.stringify({ confirmName: deleteConfirmName })
      });
      closeDeleteDialog();
      await refreshAdmin({ logPage: 0, auditPage: 0 });
      await refreshProjects();
    });
  }

  async function deleteAdminInstance(target: AdminInstance) {
    await run(`admin-delete-instance-${target.id}`, async () => {
      await api<void>(`/api/admin/instances/${target.id}`, { method: "DELETE", headers: authHeaders });
      closeDeleteDialog();
      await refreshAdmin({ logPage: 0, auditPage: 0 });
      if (selectedProjectId) await refreshProjectDetails(selectedProjectId);
    });
  }

  function changeAdminLogUser(userId: string) {
    setAdminLogUserId(userId);
    setAdminLogPage(0);
    void refreshAdmin({ logUserId: userId, logPage: 0 });
  }

  function changeAdminAuditActor(actorId: string) {
    setAdminAuditActorId(actorId);
    setAdminAuditPage(0);
    void refreshAdmin({ auditActorId: actorId, auditPage: 0 });
  }

  function changeAdminLogPage(page: number) {
    setAdminLogPage(page);
    void refreshAdmin({ logPage: page });
  }

  function changeAdminAuditPage(page: number) {
    setAdminAuditPage(page);
    void refreshAdmin({ auditPage: page });
  }

  async function uploadSpec() {
    if (!selectedProjectId || !canWrite) return;
    await run("upload-spec", async () => {
      const version = await api<SpecVersion>(`/api/projects/${selectedProjectId}/spec-versions`, {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ name: specName, source: specSource })
      });
      setSpecVersions((current) => [version, ...current]);
      navigateView("specs");
      notify(version.status === "VALID" ? t.specUploadedValid : t.specUploadedInvalid);
      await refreshProjectDetails(selectedProjectId);
    });
  }

  async function publish(versionId: string) {
    await run(`publish-${versionId}`, async () => {
      const instance = await api<MockInstance>(`/api/spec-versions/${versionId}/publish`, {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ mode: publishMode, requireApiKey })
      });
      rememberSecrets(instance);
      setInstances((current) => [instance, ...current]);
      setSelectedInstanceId(instance.id);
      navigateView("runtime");
      notify(t.publish);
      await refreshProfiles(instance.id);
      await refreshRuntimePlane();
      if (selectedProjectId) await refreshProjectDetails(selectedProjectId);
    });
  }

  async function rotateToken() {
    if (!selectedInstance) return;
    await run("rotate-token", async () => {
      const instance = await api<MockInstance>(`/api/instances/${selectedInstance.id}/rotate-token`, {
        method: "POST",
        headers: authHeaders
      });
      rememberSecrets(instance);
      setInstances((current) => current.map((item) => (item.id === instance.id ? instance : item)));
      notify(t.tokenRotated);
    });
  }

  async function rotateApiKey() {
    if (!selectedInstance) return;
    await run("rotate-api-key", async () => {
      const instance = await api<MockInstance>(`/api/instances/${selectedInstance.id}/rotate-api-key`, {
        method: "POST",
        headers: authHeaders
      });
      rememberSecrets(instance);
      setInstances((current) => current.map((item) => (item.id === instance.id ? instance : item)));
      notify(t.apiKey);
    });
  }

  async function resetState() {
    if (!selectedInstance) return;
    await run("reset-state", async () => {
      const instance = await api<MockInstance>(`/api/instances/${selectedInstance.id}/reset-state`, {
        method: "POST",
        headers: authHeaders
      });
      setInstances((current) => current.map((item) => (item.id === instance.id ? instance : item)));
      setStateSnapshot({});
      notify(t.stateReset);
      await refreshRuntimePlane();
    });
  }

  async function refreshState(instanceId = selectedInstanceId) {
    if (!instanceId) return;
    await run("state", async () => {
      setStateSnapshot(await api<Record<string, unknown>>(`/api/instances/${instanceId}/state`, { headers: authHeaders }));
    });
  }

  async function refreshLogs(instanceId = selectedInstanceId) {
    if (!instanceId) return;
    await run("logs", async () => {
      const instanceLogs = await api<RequestLog[]>(`/api/instances/${instanceId}/logs?limit=500`, { headers: authHeaders });
      if (instanceId === selectedInstanceId) {
        setLogs(instanceLogs);
      }
      setProjectLogCount((current) => Math.max(current, instanceLogs.length));
    });
  }

  async function refreshContractRoutes(versionId: string) {
    await run("contract-routes", async () => {
      setContractRoutes(await api<ContractRoute[]>(`/api/spec-versions/${versionId}/routes`, { headers: authHeaders }));
    });
  }

  async function refreshScenarios(instanceId: string) {
    await run("scenarios", async () => {
      setScenarios(await api<MockScenario[]>(`/api/instances/${instanceId}/scenarios`, { headers: authHeaders }));
    });
  }

  async function refreshResponseRules(instanceId: string) {
    await run("response-rules", async () => {
      setResponseRules(await api<ResponseRule[]>(`/api/instances/${instanceId}/response-rules`, { headers: authHeaders }));
    });
  }

  async function refreshProfiles(instanceId: string) {
    await run("profiles", async () => {
      setProfiles(await api<MockProfile[]>(`/api/instances/${instanceId}/profiles`, { headers: authHeaders }));
    });
  }

  async function previewSmartResponse() {
    if (!routeExplorerVersionId || !selectedPreviewRoute) return;
    await run("response-preview", async () => {
      const preview = await api<ResponsePreview>(`/api/spec-versions/${routeExplorerVersionId}/response-preview`, {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({
          operationId: selectedPreviewRoute.operationId,
          method: selectedPreviewRoute.method,
          pathTemplate: selectedPreviewRoute.pathTemplate,
          statusCode: previewStatusCode,
          contentType: previewContentType,
          exampleName: previewExampleName || null,
          seed: previewSeed || null
        })
      });
      setResponsePreview(preview);
    });
  }

  async function saveInstanceSettings() {
    if (!selectedInstance) return;
    await run("instance-settings", async () => {
      const updated = await api<MockInstance>(`/api/instances/${selectedInstance.id}/settings`, {
        method: "PATCH",
        headers: authHeaders,
        body: JSON.stringify({
          rateLimitEnabled,
          rateLimitRequests,
          rateLimitWindowSeconds,
          smartResponsesEnabled,
          smartSeedMode,
          faultProfileEnabled,
          faultErrorRate,
          faultStatusCode,
          latencyMinMs,
          latencyMaxMs,
          activeProfile
        })
      });
      setInstances((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      notify(lang === "ru" ? "Настройки инстанса сохранены" : "Instance settings saved");
      await refreshProfiles(selectedInstance.id);
      await refreshRuntimePlane();
    });
  }

  async function saveProfile() {
    if (!selectedInstance) return;
    const payload = {
      name: profileName,
      faultProfileEnabled: profileFaultEnabled,
      faultErrorRate: profileErrorRate,
      faultStatusCode: profileStatusCode,
      latencyMinMs: profileLatencyMin,
      latencyMaxMs: profileLatencyMax
    };
    await run("profile-save", async () => {
      const path = editingProfileId
        ? `/api/instances/${selectedInstance.id}/profiles/${editingProfileId}`
        : `/api/instances/${selectedInstance.id}/profiles`;
      const saved = await api<MockProfile>(path, {
        method: editingProfileId ? "PATCH" : "POST",
        headers: authHeaders,
        body: JSON.stringify(payload)
      });
      setProfiles((current) => editingProfileId
        ? current.map((profile) => profile.id === saved.id ? saved : profile)
        : [saved, ...current]);
      resetProfileForm();
      notify(lang === "ru" ? "Профиль окружения сохранен" : "Environment profile saved");
    });
  }

  async function activateProfile(profileId: string) {
    if (!selectedInstance) return;
    await run(`profile-activate-${profileId}`, async () => {
      const updated = await api<MockInstance>(`/api/instances/${selectedInstance.id}/profiles/${profileId}/activate`, {
        method: "POST",
        headers: authHeaders
      });
      setInstances((current) => current.map((item) => item.id === updated.id ? updated : item));
      setActiveProfile(updated.activeProfile);
      setFaultProfileEnabled(updated.faultProfileEnabled);
      setFaultErrorRate(updated.faultErrorRate);
      setFaultStatusCode(updated.faultStatusCode);
      setLatencyMinMs(updated.latencyMinMs);
      setLatencyMaxMs(updated.latencyMaxMs);
      notify(lang === "ru" ? "Профиль активирован без смены mock URL" : "Profile activated without changing mock URL");
      await refreshRuntimePlane();
    });
  }

  async function deleteProfile(profileId: string) {
    if (!selectedInstance) return;
    await run(`profile-delete-${profileId}`, async () => {
      await api<void>(`/api/instances/${selectedInstance.id}/profiles/${profileId}`, {
        method: "DELETE",
        headers: authHeaders
      });
      setProfiles((current) => current.filter((profile) => profile.id !== profileId));
      if (editingProfileId === profileId) {
        resetProfileForm();
      }
      notify(lang === "ru" ? "Профиль удален" : "Profile deleted");
    });
  }

  async function saveResponseRule() {
    if (!selectedInstance) return;
    const payload = {
      name: ruleName,
      enabled: ruleEnabled,
      priority: rulePriority,
      operationId: ruleOperationId || null,
      method: ruleMethod || null,
      pathTemplate: rulePathTemplate || null,
      fieldPath: ruleFieldPath,
      type: ruleType,
      fixedValue: parseJsonOrText(ruleFixedValue),
      minValue: ruleMinValue,
      maxValue: ruleMaxValue,
      enumValues: ruleEnumValues.split(",").map((value) => parseJsonOrText(value.trim())).filter((value) => String(value ?? "").length > 0),
      template: ruleTemplate
    };
    await run("response-rule-save", async () => {
      const path = editingRuleId
        ? `/api/instances/${selectedInstance.id}/response-rules/${editingRuleId}`
        : `/api/instances/${selectedInstance.id}/response-rules`;
      const saved = await api<ResponseRule>(path, {
        method: editingRuleId ? "PATCH" : "POST",
        headers: authHeaders,
        body: JSON.stringify(payload)
      });
      setResponseRules((current) => editingRuleId
        ? current.map((item) => (item.id === saved.id ? saved : item))
        : [saved, ...current]);
      resetResponseRuleForm();
      notify(lang === "ru" ? "Правило ответа сохранено" : "Response rule saved");
      if (selectedProjectId) await refreshProjectDetails(selectedProjectId);
      await previewSmartResponse();
    });
  }

  async function deleteResponseRule(ruleId: string) {
    if (!selectedInstance) return;
    await run(`response-rule-delete-${ruleId}`, async () => {
      await api<void>(`/api/instances/${selectedInstance.id}/response-rules/${ruleId}`, {
        method: "DELETE",
        headers: authHeaders
      });
      setResponseRules((current) => current.filter((rule) => rule.id !== ruleId));
      if (selectedProjectId) await refreshProjectDetails(selectedProjectId);
    });
  }

  async function saveScenario() {
    if (!selectedInstance) return;
    const payload = {
      name: scenarioName,
      enabled: scenarioEnabled,
      priority: scenarioPriority,
      operationId: scenarioOperationId || null,
      method: scenarioMethod || null,
      pathTemplate: scenarioPathTemplate || null,
      statusCode: scenarioStatusCode || null,
      contentType: scenarioContentType,
      body: parseJsonOrText(scenarioBody),
      headers: {},
      delayMs: scenarioDelayMs
    };
    await run("scenario-save", async () => {
      const path = editingScenarioId
        ? `/api/instances/${selectedInstance.id}/scenarios/${editingScenarioId}`
        : `/api/instances/${selectedInstance.id}/scenarios`;
      const saved = await api<MockScenario>(path, {
        method: editingScenarioId ? "PATCH" : "POST",
        headers: authHeaders,
        body: JSON.stringify(payload)
      });
      setScenarios((current) => editingScenarioId
        ? current.map((item) => (item.id === saved.id ? saved : item))
        : [saved, ...current]);
      setEditingScenarioId("");
      setScenarioName("Slow 500 example");
      setScenarioEnabled(true);
      setScenarioPriority(100);
      notify(lang === "ru" ? "Сценарий сохранён" : "Scenario saved");
      if (selectedProjectId) await refreshProjectDetails(selectedProjectId);
    });
  }

  async function deleteScenario(scenarioId: string) {
    if (!selectedInstance) return;
    await run(`scenario-delete-${scenarioId}`, async () => {
      await api<void>(`/api/instances/${selectedInstance.id}/scenarios/${scenarioId}`, {
        method: "DELETE",
        headers: authHeaders
      });
      setScenarios((current) => current.filter((scenario) => scenario.id !== scenarioId));
      if (selectedProjectId) await refreshProjectDetails(selectedProjectId);
    });
  }

  function editScenario(scenario: MockScenario) {
    setEditingScenarioId(scenario.id);
    setScenarioName(scenario.name);
    setScenarioEnabled(scenario.enabled);
    setScenarioPriority(scenario.priority);
    setScenarioOperationId(scenario.operationId ?? "");
    setScenarioMethod(scenario.method ?? "GET");
    setScenarioPathTemplate(scenario.pathTemplate ?? "/orders");
    setScenarioStatusCode(scenario.statusCode ?? 200);
    setScenarioContentType(scenario.contentType ?? "application/json");
    setScenarioDelayMs(scenario.delayMs);
    setScenarioBody(formatBody(scenario.body));
  }

  function editResponseRule(rule: ResponseRule) {
    setEditingRuleId(rule.id);
    setRuleName(rule.name);
    setRuleEnabled(rule.enabled);
    setRulePriority(rule.priority);
    setRuleOperationId(rule.operationId ?? "");
    setRuleMethod(rule.method ?? "GET");
    setRulePathTemplate(rule.pathTemplate ?? "/orders");
    setRuleFieldPath(rule.fieldPath);
    setRuleType(rule.type || "FIXED");
    setRuleFixedValue(formatBody(rule.fixedValue));
    setRuleMinValue(rule.minValue ?? 1);
    setRuleMaxValue(rule.maxValue ?? 100);
    setRuleEnumValues((rule.enumValues ?? []).map((value) => typeof value === "string" ? value : JSON.stringify(value)).join(", "));
    setRuleTemplate(rule.template ?? "{{query.name}}");
  }

  function resetResponseRuleForm() {
    setEditingRuleId("");
    setRuleName("VIP customer email");
    setRuleEnabled(true);
    setRulePriority(100);
    setRuleFieldPath("customer.email");
    setRuleType("FIXED");
    setRuleFixedValue("\"vip@example.com\"");
    setRuleMinValue(1);
    setRuleMaxValue(100);
    setRuleEnumValues("pending, paid, cancelled");
    setRuleTemplate("{{query.name}}");
  }

  function editProfile(profile: MockProfile) {
    setEditingProfileId(profile.id);
    setProfileName(profile.name);
    setProfileFaultEnabled(profile.faultProfileEnabled);
    setProfileErrorRate(profile.faultErrorRate);
    setProfileStatusCode(profile.faultStatusCode);
    setProfileLatencyMin(profile.latencyMinMs);
    setProfileLatencyMax(profile.latencyMaxMs);
  }

  function resetProfileForm() {
    setEditingProfileId("");
    setProfileName("qa-fast-fail");
    setProfileFaultEnabled(true);
    setProfileErrorRate(10);
    setProfileStatusCode(500);
    setProfileLatencyMin(120);
    setProfileLatencyMax(450);
  }

  function applyScenarioPreset(kind: "success" | "payment-error" | "empty-list" | "delay") {
    const route = selectedPreviewRoute ?? contractRoutes[0];
    if (route) {
      setScenarioMethod(route.method);
      setScenarioPathTemplate(route.pathTemplate);
      setScenarioOperationId(route.operationId ?? "");
    }
    setScenarioEnabled(true);
    if (kind === "success") {
      setScenarioName(lang === "ru" ? "Успешный заказ" : "Successful order");
      setScenarioStatusCode(200);
      setScenarioPriority(300);
      setScenarioDelayMs(0);
      setScenarioBody('{"id":"{{path.id}}","status":"paid","trace":"{{header.x-trace-id}}","requestId":"{{uuid}}"}');
    }
    if (kind === "payment-error") {
      setScenarioName(lang === "ru" ? "Ошибка оплаты" : "Payment error");
      setScenarioStatusCode(402);
      setScenarioPriority(320);
      setScenarioDelayMs(0);
      setScenarioBody('{"error":"payment_failed","message":"Card was declined","requestId":"{{uuid}}"}');
    }
    if (kind === "empty-list") {
      setScenarioName(lang === "ru" ? "Пустой список" : "Empty list");
      setScenarioStatusCode(200);
      setScenarioPriority(250);
      setScenarioDelayMs(0);
      setScenarioBody("[]");
    }
    if (kind === "delay") {
      setScenarioName(lang === "ru" ? "Задержка ответа" : "Delayed response");
      setScenarioStatusCode(200);
      setScenarioPriority(180);
      setScenarioDelayMs(1200);
      setScenarioBody('{"ok":true,"delayed":true,"time":"{{now}}"}');
    }
  }

  function applyRulePreset(kind: "random-price" | "template-email") {
    const route = selectedPreviewRoute ?? contractRoutes[0];
    if (route) {
      setRuleMethod(route.method);
      setRulePathTemplate(route.pathTemplate);
      setRuleOperationId(route.operationId ?? "");
    }
    setRuleEnabled(true);
    if (kind === "random-price") {
      setRuleName(lang === "ru" ? "Случайная цена" : "Random price");
      setRuleType("RANDOM_INT");
      setRuleFieldPath("items[0].price");
      setRulePriority(180);
      setRuleMinValue(100);
      setRuleMaxValue(9999);
    }
    if (kind === "template-email") {
      setRuleName(lang === "ru" ? "Email из запроса" : "Email from request");
      setRuleType("TEMPLATE");
      setRuleFieldPath("customer.email");
      setRulePriority(160);
      setRuleTemplate("{{query.name}}@example.com");
    }
  }

  async function addMember() {
    if (!selectedProject || !canOwn) return;
    await run("add-member", async () => {
      setMembers(await api<ProjectMember[]>(`/api/projects/${selectedProject.id}/members`, {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ identifier: memberEmail, role: memberRole })
      }));
      setMemberEmail("");
      await refreshProjectDetails(selectedProject.id);
    });
  }

  async function deleteProject(project: Project) {
    await run("delete-project", async () => {
      await api<void>(`/api/projects/${project.id}`, {
        method: "DELETE",
        headers: authHeaders,
        body: JSON.stringify({ confirmName: deleteConfirmName })
      });
      const remainingProjects = projects.filter((item) => item.id !== project.id);
      const nextProject = remainingProjects[0] ?? null;
      setProjects(remainingProjects);
      setSelectedProjectId(nextProject?.id ?? "");
      setSpecVersions([]);
      setInstances([]);
      setMembers([]);
      setAudit([]);
      setLogs([]);
      setProjectLogCount(0);
      closeDeleteDialog();
      navigateView("overview", false, nextProject);
      notify(t.projectDeleted);
    });
  }

  async function deleteSpecVersion(version: SpecVersion) {
    await run("delete-spec", async () => {
      await api<void>(`/api/spec-versions/${version.id}`, { method: "DELETE", headers: authHeaders });
      setSpecVersions((current) => current.filter((item) => item.id !== version.id));
      setInstances((current) => current.filter((item) => item.specVersionId !== version.id));
      closeDeleteDialog();
      notify(t.specDeleted);
      if (selectedProjectId) await refreshProjectDetails(selectedProjectId);
    });
  }

  async function deleteInstance(instance: MockInstance) {
    await run("delete-instance", async () => {
      await api<void>(`/api/instances/${instance.id}`, { method: "DELETE", headers: authHeaders });
      setInstances((current) => current.filter((item) => item.id !== instance.id));
      setLogs([]);
      setSelectedInstanceId("");
      closeDeleteDialog();
      if (selectedProjectId) await refreshProjectDetails(selectedProjectId);
    });
  }

  async function deleteMember(member: ProjectMember) {
    if (!selectedProject) return;
    await run("delete-member", async () => {
      setMembers(await api<ProjectMember[]>(`/api/projects/${selectedProject.id}/members/${member.userId}`, {
        method: "DELETE",
        headers: authHeaders
      }));
      closeDeleteDialog();
      await refreshProjectDetails(selectedProject.id);
    });
  }

  async function callMock() {
    if (!selectedInstance) return;
    const publicUrl = issuedUrls[selectedInstance.id] ?? selectedInstance.publicUrl;
    if (!publicUrl) {
      notify(t.noUrl);
      return;
    }
    await run("call-mock", async () => {
      const headers: Record<string, string> = {};
      if (!["GET", "DELETE"].includes(mockMethod)) {
        headers["Content-Type"] = "application/json";
      }
      if (selectedInstance.requireApiKey && mockApiKeyInput) {
        headers["X-Mock-Api-Key"] = mockApiKeyInput;
      }
      const response = await fetch(`${publicUrl}${ensurePath(mockPath)}`, {
        method: mockMethod,
        headers,
        body: ["GET", "DELETE"].includes(mockMethod) ? undefined : mockBody
      });
      const responseText = await response.text();
      setMockResponse(`${response.status} ${response.statusText}\n${responseText}`);
      await refreshLogs();
      await refreshState();
    });
  }

  async function copy(value: string | null | undefined) {
    if (!value) return;
    await navigator.clipboard.writeText(value);
    notify(t.copied);
  }

  function handleFile(file: File | null) {
    if (!file) return;
    setSpecName(file.name);
    file.text().then(setSpecSource).catch(showError);
  }

  function rememberSecrets(instance: MockInstance) {
    if (instance.publicUrl) {
      setIssuedUrls((current) => ({ ...current, [instance.id]: instance.publicUrl as string }));
    }
    if (instance.mockApiKey) {
      setIssuedApiKeys((current) => ({ ...current, [instance.id]: instance.mockApiKey as string }));
      setMockApiKeyInput(instance.mockApiKey);
    }
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
    if (deleteTarget.kind === "instance") {
      const instance = instances.find((item) => item.id === deleteTarget.id);
      if (instance) void deleteInstance(instance);
      return;
    }
    if (deleteTarget.kind === "admin-user") {
      const target = adminUsers.find((item) => item.id === deleteTarget.id);
      if (target) void deleteAdminUser(target);
      return;
    }
    if (deleteTarget.kind === "admin-project") {
      const target = adminProjects.find((item) => item.id === deleteTarget.id);
      if (target) void deleteAdminProject(target);
      return;
    }
    if (deleteTarget.kind === "admin-instance") {
      const target = adminInstances.find((item) => item.id === deleteTarget.id);
      if (target) void deleteAdminInstance(target);
      return;
    }
    const member = members.find((item) => item.userId === deleteTarget.id);
    if (member) void deleteMember(member);
  }

  function logout() {
    localStorage.removeItem("msaas.token");
    localStorage.removeItem("msaas.user");
    setToken("");
    setUser(null);
    setProjects([]);
    setSelectedProjectId("");
    setSpecVersions([]);
    setInstances([]);
    setMembers([]);
    setAudit([]);
    setLogs([]);
    setPublicView("landing");
    setAuthStep("identifier");
    setToast(null);
  }

  function notify(message: string) {
    const text = String(message ?? "").trim();
    if (!text) return;
    setToast({ id: Date.now(), text });
  }

  function showError(error: unknown) {
    notify(error instanceof Error ? error.message : String(error));
  }

  function deleteTitle(target: DeleteTarget) {
    if (target.kind === "project" || target.kind === "admin-project") return t.deleteProject;
    if (target.kind === "spec") return t.deleteSpec;
    if (target.kind === "member") return t.deleteMember;
    if (target.kind === "admin-user") return t.adminDeleteUser;
    return t.deleteInstance;
  }

  function deleteDetail(target: DeleteTarget) {
    if (target.kind === "project" || target.kind === "admin-project") return t.deleteProjectDetail;
    if (target.kind === "spec") return t.deleteSpecDetail;
    if (target.kind === "member") return t.deleteMemberDetail;
    if (target.kind === "admin-user") return t.deleteWarning;
    return t.deleteInstanceDetail;
  }

  function requiresNameConfirmation(target: DeleteTarget) {
    return target.kind === "project" || target.kind === "admin-project" || target.kind === "admin-user";
  }

  function pathForView(nextView: ViewKey, projectOverride: Project | null = selectedProject) {
    const segment = projectViewSegments[nextView];
    if (nextView === "admin" || nextView === "settings" || !segment || !projectOverride) {
      return viewRoutes[nextView];
    }
    return `/console/${projectRouteSegment(projectOverride)}/${segment}`;
  }

  function navigateView(nextView: ViewKey, replace = false, projectOverride: Project | null = selectedProject) {
    setView(nextView);
    writeRoute(pathForView(nextView, projectOverride), replace);
  }

  function selectProject(project: Project) {
    setSelectedProjectId(project.id);
    if (isProjectView(view)) {
      writeRoute(pathForView(view, project));
    }
  }

  function openLoginScreen(replace = false) {
    setPublicView("auth");
    setAuthStep("identifier");
    writeRoute("/login", replace);
    setToast(null);
  }

  function openRegisterScreen(replace = false) {
    setPublicView("auth");
    setAuthStep("register");
    writeRoute("/register", replace);
    setToast(null);
  }

  function backToLanding() {
    setPublicView("landing");
    setAuthStep("identifier");
    writeRoute("/");
    setToast(null);
  }

  function backToIdentifier() {
    setAuthStep("identifier");
    setPassword("");
    writeRoute("/login");
    setToast(null);
  }

  const shellControls = (
    <div className="shell-controls" aria-label="preferences">
      <button className="icon-button" title={t.theme} onClick={() => setTheme(theme === "light" ? "dark" : "light")}>
        {theme === "light" ? <Moon size={18} /> : <Sun size={18} />}
      </button>
      <button className="segmented-button" title={t.language} onClick={() => setLang(lang === "ru" ? "en" : "ru")}>
        <Globe2 size={16} />
        {lang.toUpperCase()}
      </button>
      {token && (
        <button className="ghost-button" onClick={logout}>
          <LogOut size={17} />
          {t.logout}
        </button>
      )}
    </div>
  );

  if (!token) {
    if (publicView === "landing") {
      return (
        <main className="public-shell landing-shell">
          <header className="auth-topbar landing-topbar">
            <Brand />
            <nav className="landing-nav" aria-label="landing navigation">
              <a href="#landing-features">{t.landingNavFeatures}</a>
              <a href="#landing-workflow">{t.landingNavWorkflow}</a>
              <a href="#landing-security">{t.landingNavSecurity}</a>
            </nav>
            <div className="shell-controls" aria-label="preferences">
              <button className="icon-button" title={t.theme} onClick={() => setTheme(theme === "light" ? "dark" : "light")}>
                {theme === "light" ? <Moon size={18} /> : <Sun size={18} />}
              </button>
              <button className="segmented-button" title={t.language} onClick={() => setLang(lang === "ru" ? "en" : "ru")}>
                <Globe2 size={16} />
                {lang.toUpperCase()}
              </button>
              <button className="soft-button" onClick={() => openLoginScreen()}>{t.login}</button>
              <button className="primary-button" onClick={() => openRegisterScreen()}>{t.register}</button>
            </div>
          </header>
          <section className="landing-hero">
            <div className="landing-copy">
              <p className="eyebrow">{t.landingHeroBadge}</p>
              <h1>{t.heroTitle}</h1>
              <p>{t.heroCopy}</p>
              <div className="landing-hero-actions">
                <button className="primary-button" onClick={() => openRegisterScreen()}><KeyRound size={17} />{t.landingPrimaryCta}</button>
                <button className="soft-button" onClick={() => openLoginScreen()}><Plus size={17} />{t.landingSecondaryCta}</button>
              </div>
              <div className="hero-proof-row">
                {t.landingHeroProofs.map((item) => (
                  <span key={item}><CheckCircle2 size={15} />{item}</span>
                ))}
              </div>
            </div>
            <div className="hero-product-scene" aria-hidden="true">
              <div className="scene-window scene-window-main">
                <div className="scene-window-bar">
                  <span />
                  <span />
                  <span />
                  <strong>MSaaS Console</strong>
                </div>
                <div className="scene-project-row">
                  <FileJson size={17} />
                  <span>
                    <strong>{t.landingSceneProject}</strong>
                    <small>OpenAPI 3.0 · VALID · 5 routes</small>
                  </span>
                  <StatusPill ok text="READY" />
                </div>
                <div className="scene-request-row">
                  <span className="method-badge get">GET</span>
                  <strong>{t.landingSceneRequest}</strong>
                  <small>{t.landingSceneStatus}</small>
                </div>
                <pre>{`{
  "id": "ord_1024",
  "title": "Demo order",
  "paid": true
}`}</pre>
              </div>
              <div className="scene-window scene-window-side">
                <div className="scene-mini-stat"><ShieldCheck size={16} /><strong>{t.landingPrivate}</strong><small>{t.landingPrivateNote}</small></div>
                <div className="scene-mini-stat"><Zap size={16} /><strong>{t.landingRuntime}</strong><small>{t.landingRuntimeNote}</small></div>
                <div className="scene-mini-stat"><Activity size={16} /><strong>{t.landingDiagnostics}</strong><small>{t.landingDiagnosticsNote}</small></div>
              </div>
              <span className="scene-pulse one" />
              <span className="scene-pulse two" />
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
              <p className="eyebrow">Why MSaaS</p>
              <h2>{t.landingProblemTitle}</h2>
              <p>{t.landingProblemCopy}</p>
            </div>
            <div className="landing-card-grid">
              {t.landingProblems.map((item, index) => (
                <LandingCard
                  key={item.title}
                  icon={[<AlertTriangle size={19} />, <Layers3 size={19} />, <Activity size={19} />][index]}
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
                    icon={[<ShieldCheck size={19} />, <KeyRound size={19} />, <Database size={19} />, <History size={19} />][index]}
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
              <button className="primary-button" onClick={() => openRegisterScreen()}><KeyRound size={17} />{t.landingPrimaryCta}</button>
              <button className="soft-button" onClick={() => openRegisterScreen()}><Plus size={17} />{t.register}</button>
            </div>
          </section>
        </main>
      );
    }

    return (
      <main className="auth-shell">
        <header className="auth-topbar">
          <Brand />
          {shellControls}
        </header>
        <section className="auth-grid">
          <div className="auth-copy">
            <p className="eyebrow">MSaaS Console</p>
            <h1>{t.subtitle}</h1>
            <div className="pipeline" aria-label="pipeline">
              <FlowStep icon={<FileJson size={20} />} label="OpenAPI" />
              <FlowStep icon={<Zap size={20} />} label="Warm slot" />
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
                <button className="primary-button" onClick={() => auth("/api/auth/login")} disabled={busy.auth}>
                  {busy.auth ? <Loader2 className="spin" size={17} /> : <KeyRound size={17} />}
                  {t.login}
                </button>
              )}
              {authStep === "register" && (
                <button className="primary-button" onClick={() => auth("/api/auth/register")} disabled={busy.auth}>
                  {busy.auth ? <Loader2 className="spin" size={17} /> : <Plus size={17} />}
                  {t.register}
                </button>
              )}
              {authStep === "identifier" ? (
                <button className="soft-button" onClick={backToLanding}>{t.back}</button>
              ) : (
                <button className="soft-button" onClick={backToIdentifier}>{t.back}</button>
              )}
            </div>
            {toast?.text && <p className="notice">{toast.text}</p>}
          </section>
        </section>
      </main>
    );
  }

  return (
    <main className="console-shell">
      <section className="main-column">
        <header className="console-brandbar">
          <div className="console-brand-group">
            <Brand />
            <div className="project-create-block">
              <button className="soft-button sidebar-create-toggle" onClick={() => setProjectCreateOpen((open) => !open)}>
                <Plus size={17} />
                {t.createNewProject}
              </button>
              {projectCreateOpen && (
                <div className="create-box create-popover">
                  <label>{t.name}<input value={projectName} onChange={(event) => setProjectName(event.target.value)} /></label>
                  <label>{t.description}<input value={projectDescription} onChange={(event) => setProjectDescription(event.target.value)} /></label>
                  <button className="primary-button full" onClick={createProject} disabled={busy["create-project"]}>
                    {busy["create-project"] ? <Loader2 className="spin" size={17} /> : <Plus size={17} />}
                    {t.createProject}
                  </button>
                </div>
              )}
            </div>
          </div>
          <div className="topbar-actions">
            <div className="account-chip" title={user?.email ?? ""}>
              <span className="account-avatar">{displayUser(user ?? {}).slice(0, 2).toUpperCase()}</span>
              <span>
                <strong>{displayUser(user ?? {})}</strong>
                <small>{user?.systemRole ?? "USER"}</small>
              </span>
            </div>
            {isAdmin && (
              <a href={viewRoutes.admin} className={cx("soft-button stable-action", view === "admin" && "active")} onClick={(event) => { event.preventDefault(); navigateView("admin"); }}>
                <ShieldCheck size={16} />
                {t.admin}
              </a>
            )}
            {shellControls}
          </div>
        </header>

        <header className="console-topbar">
          <div>
            <p className="eyebrow">{view === "admin" ? t.admin : t.workspace}</p>
            <h1>{view === "admin" ? t.admin : selectedProject?.name ?? t.noProject}</h1>
          </div>
          <div className="topbar-actions">
            {selectedProject && view !== "admin" && <RoleBadge role={selectedProject.role} />}
            {canOwn && selectedProject && view !== "admin" && (
              <button className="danger-button" onClick={() => openDeleteDialog({ kind: "project", id: selectedProject.id, name: selectedProject.name })}>
                <Trash2 size={17} />
                {t.deleteProject}
              </button>
            )}
          </div>
        </header>

        <nav className="view-tabs" aria-label="sections">
          {visibleViews.map((item) => (
            <a key={item.key} href={pathForView(item.key)} className={cx("tab-button", view === item.key && "active")} onClick={(event) => { event.preventDefault(); navigateView(item.key); }}>
              {item.icon}
              {t[item.key]}
            </a>
          ))}
        </nav>

        {toast?.text && (
          <div className="notice-row">
            <span>{toast.text}</span>
            <button className="ghost-button tiny" onClick={() => setToast(null)}>{t.clear}</button>
          </div>
        )}

        {!selectedProject && view !== "admin" && view !== "settings" && <EmptyState icon={<Layers3 size={32} />} text={t.emptyProjects} />}
        {selectedProject && view === "overview" && renderOverview()}
        {selectedProject && view === "specs" && renderSpecs()}
        {selectedProject && view === "runtime" && renderRuntime()}
        {selectedProject && view === "logs" && renderLogs()}
        {selectedProject && view === "access" && renderAccess()}
        {view === "settings" && renderSettings()}
        {view === "admin" && isAdmin && renderAdmin()}
      </section>

      {selectedLog && (
        <aside className="drawer" aria-label="log detail">
          <div className="drawer-heading">
            <div>
              <p className="eyebrow">{t.logs}</p>
              <h2>{selectedLog.method} {selectedLog.path}</h2>
            </div>
            <button className="icon-button" onClick={() => setSelectedLog(null)}><XCircle size={18} /></button>
          </div>
          <Detail label="Status" value={String(selectedLog.responseStatus)} />
          <Detail label="Latency" value={`${selectedLog.latencyMs}ms`} />
          <Detail label="Query" value={selectedLog.queryString || "-"} />
          <Detail label={lang === "ru" ? "Источник" : "Source"} value={sourceLabel(selectedLog.responseSource, lang)} />
          <Detail label={lang === "ru" ? "Профиль" : "Profile"} value={selectedLog.profileName || "-"} />
          <pre>{JSON.stringify({ headers: selectedLog.requestHeaders, body: selectedLog.requestBody, error: selectedLog.error, appliedRuleIds: selectedLog.appliedRuleIds }, null, 2)}</pre>
        </aside>
      )}

      {deleteTarget && (
        <div className="modal-backdrop" role="presentation">
          <section className="confirm-dialog" role="dialog" aria-modal="true" aria-label={deleteTitle(deleteTarget)}>
            <div className="dialog-icon"><AlertTriangle size={24} /></div>
            <div>
              <p className="eyebrow">{t.deleteWarning}</p>
              <h2>{deleteTitle(deleteTarget)}</h2>
            </div>
            <p className="dialog-copy">{deleteDetail(deleteTarget)}</p>
            <code className="delete-target-name">{deleteTarget.name}</code>
            {requiresNameConfirmation(deleteTarget) && (
              <label>{t.confirmProjectName}<input value={deleteConfirmName} onChange={(event) => setDeleteConfirmName(event.target.value)} autoFocus /></label>
            )}
            <div className="dialog-actions">
              <button className="soft-button" onClick={closeDeleteDialog}>{t.cancel}</button>
              <button
                className="danger-button"
                disabled={requiresNameConfirmation(deleteTarget) && deleteConfirmName !== deleteTarget.name}
                onClick={confirmDeletion}
              >
                <Trash2 size={17} />
                {t.confirmDelete}
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  );

  function renderOverview() {
    return (
      <>
        <section className="stats-grid">
          <Metric icon={<Layers3 size={19} />} label={t.allProjects} value={projects.length} />
          <Metric icon={<Users size={19} />} label={t.members} value={allProjectMembers} />
          <Metric icon={<FileJson size={19} />} label={t.specs} value={specVersions.length} />
          <Metric icon={<Activity size={19} />} label={t.logs} value={projectLogCount} />
        </section>
        <section className="overview-grid">
          <article className="surface overview-panel">
            <div className="surface-heading">
              <div>
                <p className="eyebrow">{t.allProjectsOverview}</p>
                <h2>{t.allProjects}</h2>
              </div>
            </div>
            <div className="stack-list panel-scroll compact-scroll">
              {projectListPage.items.map((project) => (
                <button
                  className={cx("project-button", project.id === selectedProjectId && "active")}
                  key={project.id}
                  onClick={() => selectProject(project)}
                >
                  <span className="project-avatar">{project.name.slice(0, 2).toUpperCase()}</span>
                  <span>
                    <strong>{project.name}</strong>
                    <small>{project.role} · {project.memberCount} {t.members.toLowerCase()}</small>
                  </span>
                </button>
              ))}
            </div>
            <Pager
              page={projectListPage.page}
              totalPages={projectListPage.totalPages}
              total={projectListPage.total}
              previousLabel={t.pagePrevious}
              nextLabel={t.pageNext}
              onPage={setProjectPage}
            />
          </article>
          <article className="surface overview-panel">
            <div className="surface-heading">
              <div>
                <p className="eyebrow">{t.currentProject}</p>
                <h2>{selectedProject?.name}</h2>
              </div>
            </div>
            <div className="detail-grid">
              <Detail label={t.role} value={selectedProject?.role ?? "-"} />
              <Detail label={t.specs} value={String(specVersions.length)} />
              <Detail label={t.instances} value={String(instances.length)} />
              <Detail label={t.logs} value={String(projectLogCount)} />
            </div>
          </article>
          <article className="surface overview-panel">
            <div className="surface-heading">
              <div>
                <p className="eyebrow">{t.runtimePlane}</p>
                <h2>{t.workers}</h2>
              </div>
              <button className="soft-button" onClick={refreshRuntimePlane}><RefreshCw size={16} />{t.refresh}</button>
            </div>
            <div className="worker-list panel-scroll compact-scroll">
              {runtimeWorkers.map((worker) => (
                <div className="worker-row" key={worker.id ?? worker.workerKey}>
                  <span className={cx("status-dot", worker.status.toLowerCase())} />
                  <strong>{worker.workerKey}</strong>
                  <span>{worker.status}</span>
                  <small>{worker.slotCount} slots</small>
                </div>
              ))}
              {runtimeWorkers.length === 0 && <SkeletonRows count={2} />}
            </div>
          </article>
          <article className="surface overview-panel">
            <div className="surface-heading">
              <div>
                <p className="eyebrow">{t.audit}</p>
                <h2>{t.audit}</h2>
              </div>
              <button className="soft-button" onClick={() => selectedProjectId && refreshProjectDetails(selectedProjectId)}><RefreshCw size={16} />{t.refresh}</button>
            </div>
            <div className="panel-scroll compact-scroll">
              <AuditList items={projectAuditListPage.items} emptyText={t.emptyAudit} />
            </div>
            <Pager
              page={projectAuditListPage.page}
              totalPages={projectAuditListPage.totalPages}
              total={projectAuditListPage.total}
              previousLabel={t.pagePrevious}
              nextLabel={t.pageNext}
              onPage={setProjectAuditPage}
            />
          </article>
        </section>
      </>
    );
  }

  function renderSpecs() {
    return (
      <section className="split-grid">
        <article className="surface spec-editor spec-upload-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.apiContract}</p>
              <h2>{t.uploadSpec}</h2>
            </div>
            <button className="primary-button" onClick={uploadSpec} disabled={!canWrite || busy["upload-spec"]}>
              {busy["upload-spec"] ? <Loader2 className="spin" size={17} /> : <Upload size={17} />}
              {t.uploadSpec}
            </button>
          </div>
          {!canWrite && <p className="muted">{t.viewerHint}</p>}
          <div className="form-grid two">
            <label>{t.specName}<input value={specName} onChange={(event) => setSpecName(event.target.value)} disabled={!canWrite} /></label>
            <label className="file-picker">
              <span>{t.apiContract}</span>
              <input type="file" accept=".yaml,.yml,.json" onChange={(event) => handleFile(event.target.files?.[0] ?? null)} disabled={!canWrite} />
              <span className="file-picker-box">
                <Upload size={17} />
                <strong>{t.chooseFile}</strong>
                <small>{t.selectedFile}: {specName}</small>
              </span>
            </label>
          </div>
          <textarea className="code-editor" value={specSource} onChange={(event) => setSpecSource(event.target.value)} spellCheck={false} disabled={!canWrite} />
        </article>

        <article className="surface spec-publish-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.specs}</p>
              <h2>{t.publish}</h2>
            </div>
            <div className="inline-controls">
              <select value={publishMode} onChange={(event) => setPublishMode(event.target.value as InstanceMode)} disabled={!canWrite}>
                <option value="STATEFUL">STATEFUL</option>
                <option value="STATELESS">STATELESS</option>
              </select>
              <label className="check-row">
                <input type="checkbox" checked={requireApiKey} onChange={(event) => setRequireApiKey(event.target.checked)} disabled={!canWrite} />
                {t.requireApiKey}
              </label>
            </div>
          </div>
          <div className="stack-list panel-scroll">
            {busy.details && <SkeletonRows count={3} />}
            {!busy.details && specVersions.map((version) => (
              <div className="version-card" key={version.id}>
                <div className="version-meta">
                  <StatusPill ok={version.status === "VALID"} text={version.status === "VALID" ? "VALID" : "INVALID"} />
                  <strong>v{version.versionNumber}: {version.name}</strong>
                  <small>{version.routeCount} {t.routeCount}</small>
                  {version.validationErrors.length > 0 && <p className="validation-text">{version.validationErrors.join("; ")}</p>}
                </div>
                <div className="card-actions">
                  <button className="soft-button" disabled={!canWrite || version.status !== "VALID"} onClick={() => publish(version.id)}>
                    <Play size={16} />
                    {t.publish}
                  </button>
                  {canWrite && (
                    <button className="icon-button danger-icon" title={t.deleteSpec} onClick={() => openDeleteDialog({ kind: "spec", id: version.id, name: version.name })}>
                      <Trash2 size={16} />
                    </button>
                  )}
                </div>
              </div>
            ))}
            {!busy.details && specVersions.length === 0 && <EmptyState icon={<FileJson size={28} />} text={t.emptySpecs} />}
          </div>
          <div className="route-explorer">
            <div className="surface-heading compact-heading">
              <div>
                <p className="eyebrow">Route explorer</p>
                <h2>{lang === "ru" ? "Маршруты контракта" : "Contract routes"}</h2>
              </div>
              <select value={routeExplorerVersionId} onChange={(event) => setRouteExplorerVersionId(event.target.value)}>
                {specVersions.map((version) => (
                  <option value={version.id} key={version.id}>v{version.versionNumber}: {version.name}</option>
                ))}
              </select>
            </div>
            <div className="stack-list panel-scroll route-list">
              {contractRoutes.map((route) => (
                <div className="route-card" key={`${route.method}-${route.pathTemplate}`}>
                  <span className={cx("method-badge", route.method.toLowerCase())}>{route.method}</span>
                  <strong>{route.pathTemplate}</strong>
                  <small>{route.operationId || "operation"} · {route.requestBodyRequired ? "body required" : "body optional"}</small>
                  <small>query: {route.requiredQueryParameters.join(", ") || "-"} · headers: {route.requiredHeaderParameters.join(", ") || "-"}</small>
                  <small>{route.responses.map((response) => `${response.statusCode} ${response.contentType}${response.examples.length ? ` examples: ${response.examples.join(", ")}` : ""}`).join(" · ")}</small>
                </div>
              ))}
              {contractRoutes.length === 0 && <EmptyState icon={<FileJson size={28} />} text={lang === "ru" ? "Выбери валидную спецификацию." : "Select a valid specification."} compact />}
            </div>
          </div>
          <div className="response-preview-panel">
            <div className="surface-heading compact-heading">
              <div>
                <p className="eyebrow">Smart responses</p>
                <h2>{lang === "ru" ? "Preview ответа" : "Response preview"}</h2>
              </div>
              <button className="soft-button" onClick={previewSmartResponse} disabled={!selectedPreviewRoute || busy["response-preview"]}>
                {busy["response-preview"] ? <Loader2 className="spin" size={16} /> : <Play size={16} />}
                Preview
              </button>
            </div>
            <div className="preview-controls">
              <label>{lang === "ru" ? "Маршрут" : "Route"}
                <select value={previewRouteKey} onChange={(event) => setPreviewRouteKey(event.target.value)}>
                  {contractRoutes.map((route) => (
                    <option value={routeKey(route)} key={routeKey(route)}>{route.method} {route.pathTemplate}</option>
                  ))}
                </select>
              </label>
              <label>Status
                <select value={previewStatusCode} onChange={(event) => setPreviewStatusCode(Number(event.target.value))}>
                  {(selectedPreviewRoute?.responses ?? []).map((response) => (
                    <option value={response.statusCode} key={`${response.statusCode}-${response.contentType}`}>{response.statusCode} {response.schemaAvailable ? "schema" : "example"}</option>
                  ))}
                </select>
              </label>
              <label>Content-Type
                <select value={previewContentType} onChange={(event) => setPreviewContentType(event.target.value)}>
                  {[...new Set((selectedPreviewRoute?.responses ?? []).flatMap((response) => response.contentTypes?.length ? response.contentTypes : [response.contentType]))].map((contentType) => (
                    <option value={contentType} key={contentType}>{contentType}</option>
                  ))}
                </select>
              </label>
              <label>Example
                <select value={previewExampleName} onChange={(event) => setPreviewExampleName(event.target.value)}>
                  <option value="">AUTO</option>
                  {(selectedPreviewRoute?.responses.find((response) => response.statusCode === previewStatusCode)?.examples ?? []).map((example) => (
                    <option value={example} key={example}>{example}</option>
                  ))}
                </select>
              </label>
              <label>Seed<input value={previewSeed} onChange={(event) => setPreviewSeed(event.target.value)} placeholder="auto" /></label>
            </div>
            <pre className="panel-pre preview-pre">{responsePreview ? JSON.stringify(responsePreview, null, 2) : "-"}</pre>
          </div>
        </article>
      </section>
    );
  }

  function renderRuntime() {
    return (
      <section className="runtime-grid">
        <article className="surface runtime-panel runtime-instance-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.instances}</p>
              <h2>{t.runtime}</h2>
            </div>
            <button className="soft-button" onClick={() => selectedProjectId && refreshProjectDetails(selectedProjectId)}><RefreshCw size={16} />{t.refresh}</button>
          </div>
          <div className="stack-list panel-scroll instance-list">
            {instances.map((instance) => (
              <button className={cx("instance-card", instance.id === selectedInstanceId && "active")} key={instance.id} onClick={() => setSelectedInstanceId(instance.id)}>
                <span className="status-dot" />
                <span>
                  <strong>{instance.status} · {instance.mode}</strong>
                  <small>{instance.routeCount} {t.routeCount} · {instance.activeProfileName || instance.activeProfile} · {t.tokenPreview}: {instance.tokenPreview}</small>
                </span>
                <code>{issuedUrls[instance.id] ?? instance.publicUrl ?? t.noUrl}</code>
              </button>
            ))}
            {instances.length === 0 && <EmptyState icon={<Server size={28} />} text={t.emptyRuntime} />}
          </div>
        </article>

        <article className="surface runtime-panel runtime-call-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.callMock}</p>
              <h2>{t.diagnostics}</h2>
            </div>
          </div>
          {selectedInstance && (
            <div className="secret-panel">
              <label>{t.tokenPreview}<input readOnly value={issuedUrls[selectedInstance.id] ?? selectedInstance.publicUrl ?? selectedInstance.tokenPreview ?? ""} /></label>
              <button className="icon-button" title={t.copied} onClick={() => copy(issuedUrls[selectedInstance.id] ?? selectedInstance.publicUrl)}><Clipboard size={17} /></button>
              <button className="soft-button" onClick={rotateToken} disabled={!canWrite}><RefreshCw size={16} />{t.rotateToken}</button>
              <button className="soft-button" onClick={resetState} disabled={!canWrite}><RotateCw size={16} />{t.resetState}</button>
              {canWrite && (
                <button className="danger-button" onClick={() => openDeleteDialog({ kind: "instance", id: selectedInstance.id, name: selectedInstance.tokenPreview })}>
                  <Trash2 size={16} />
                  {t.deleteInstance}
                </button>
              )}
            </div>
          )}
          {selectedInstance?.requireApiKey && (
            <div className="api-key-box">
              <label>{t.apiKey}<input value={mockApiKeyInput} onChange={(event) => setMockApiKeyInput(event.target.value)} placeholder={selectedInstance.apiKeyPreview ?? t.apiKeyHidden} /></label>
              <button className="icon-button" onClick={() => copy(mockApiKeyInput)}><Clipboard size={17} /></button>
              <button className="soft-button" onClick={rotateApiKey} disabled={!canWrite}><KeyRound size={16} />{t.rotateApiKey}</button>
            </div>
          )}
          {selectedInstance && (
            <div className="instance-settings-grid">
              <label className="check-row">
                <input type="checkbox" checked={rateLimitEnabled} onChange={(event) => setRateLimitEnabled(event.target.checked)} disabled={!canWrite} />
                {lang === "ru" ? "Rate limit включён" : "Rate limit enabled"}
              </label>
              <label>{lang === "ru" ? "Запросов" : "Requests"}<input type="number" min={1} value={rateLimitRequests} onChange={(event) => setRateLimitRequests(Number(event.target.value))} disabled={!canWrite} /></label>
              <label>{lang === "ru" ? "Окно, сек" : "Window, sec"}<input type="number" min={1} value={rateLimitWindowSeconds} onChange={(event) => setRateLimitWindowSeconds(Number(event.target.value))} disabled={!canWrite} /></label>
              <label className="check-row">
                <input type="checkbox" checked={smartResponsesEnabled} onChange={(event) => setSmartResponsesEnabled(event.target.checked)} disabled={!canWrite} />
                Smart responses
              </label>
              <label>Seed mode
                <select value={smartSeedMode} onChange={(event) => setSmartSeedMode(event.target.value)} disabled={!canWrite}>
                  <option value="STABLE">STABLE</option>
                  <option value="RANDOM">RANDOM</option>
                </select>
              </label>
              <label>{lang === "ru" ? "Активный профиль" : "Active profile"}
                <select value={activeProfile} onChange={(event) => setActiveProfile(event.target.value)} disabled={!canWrite}>
                  {profiles.map((profile) => (
                    <option value={profile.id} key={profile.id}>{profile.name}</option>
                  ))}
                  {profiles.length === 0 && <option value={activeProfile}>{activeProfile}</option>}
                </select>
              </label>
              <label className="check-row">
                <input type="checkbox" checked={faultProfileEnabled} onChange={(event) => setFaultProfileEnabled(event.target.checked)} disabled={!canWrite} />
                {lang === "ru" ? "Fault profile" : "Fault profile"}
              </label>
              <label>{lang === "ru" ? "Ошибки, %" : "Error rate, %"}<input type="number" min={0} max={100} value={faultErrorRate} onChange={(event) => setFaultErrorRate(Number(event.target.value))} disabled={!canWrite} /></label>
              <label>{lang === "ru" ? "Fault status" : "Fault status"}<input type="number" min={400} max={599} value={faultStatusCode} onChange={(event) => setFaultStatusCode(Number(event.target.value))} disabled={!canWrite} /></label>
              <label>{lang === "ru" ? "Latency min, ms" : "Latency min, ms"}<input type="number" min={0} value={latencyMinMs} onChange={(event) => setLatencyMinMs(Number(event.target.value))} disabled={!canWrite} /></label>
              <label>{lang === "ru" ? "Latency max, ms" : "Latency max, ms"}<input type="number" min={0} value={latencyMaxMs} onChange={(event) => setLatencyMaxMs(Number(event.target.value))} disabled={!canWrite} /></label>
              <button className="soft-button" onClick={saveInstanceSettings} disabled={!canWrite || busy["instance-settings"]}>
                {busy["instance-settings"] ? <Loader2 className="spin" size={16} /> : <Settings2 size={16} />}
                {t.save}
              </button>
            </div>
          )}
          <div className="request-line">
            <select value={mockMethod} onChange={(event) => setMockMethod(event.target.value)}>
              <option>GET</option>
              <option>POST</option>
              <option>PUT</option>
              <option>PATCH</option>
              <option>DELETE</option>
            </select>
            <input value={mockPath} onChange={(event) => setMockPath(event.target.value)} aria-label={t.mockPath} />
            <button className="primary-button" onClick={callMock} disabled={!selectedInstance || busy["call-mock"]}>
              {busy["call-mock"] ? <Loader2 className="spin" size={16} /> : <Send size={16} />}
              {t.send}
            </button>
          </div>
          <label>{t.mockBody}<textarea className="body-editor" value={mockBody} onChange={(event) => setMockBody(event.target.value)} /></label>
          <ResponseBlock title={t.response} value={mockResponse} clearLabel={t.clear} onClear={() => setMockResponse("")} />
        </article>

        <article className="surface runtime-panel profile-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">Environment profiles</p>
              <h2>{lang === "ru" ? "Профили окружений" : "Environment profiles"}</h2>
            </div>
            <button className="soft-button" onClick={() => selectedInstance && refreshProfiles(selectedInstance.id)} disabled={!selectedInstance}>
              <RefreshCw size={16} />
              {t.refresh}
            </button>
          </div>
          <p className="hint-text">{lang === "ru"
            ? "Переключай dev / qa / demo или свой профиль без пересоздания публичной mock-ссылки."
            : "Switch dev / qa / demo or a custom profile without recreating the public mock URL."}</p>
          <div className="profile-form">
            <label>{lang === "ru" ? "Название" : "Name"}<input value={profileName} onChange={(event) => setProfileName(event.target.value)} disabled={!canWrite} /></label>
            <label className="check-row">
              <input type="checkbox" checked={profileFaultEnabled} onChange={(event) => setProfileFaultEnabled(event.target.checked)} disabled={!canWrite} />
              {lang === "ru" ? "Включить ошибки" : "Enable faults"}
            </label>
            <label>{lang === "ru" ? "Ошибки, %" : "Error rate, %"}<input type="number" min={0} max={100} value={profileErrorRate} onChange={(event) => setProfileErrorRate(Number(event.target.value))} disabled={!canWrite} /></label>
            <label>{lang === "ru" ? "Статус" : "Status"}<input type="number" min={400} max={599} value={profileStatusCode} onChange={(event) => setProfileStatusCode(Number(event.target.value))} disabled={!canWrite} /></label>
            <label>{lang === "ru" ? "Min ms" : "Min ms"}<input type="number" min={0} value={profileLatencyMin} onChange={(event) => setProfileLatencyMin(Number(event.target.value))} disabled={!canWrite} /></label>
            <label>{lang === "ru" ? "Max ms" : "Max ms"}<input type="number" min={0} value={profileLatencyMax} onChange={(event) => setProfileLatencyMax(Number(event.target.value))} disabled={!canWrite} /></label>
            <button className="primary-button" onClick={saveProfile} disabled={!selectedInstance || !canWrite || busy["profile-save"]}>
              {busy["profile-save"] ? <Loader2 className="spin" size={16} /> : <Settings2 size={16} />}
              {editingProfileId ? t.save : lang === "ru" ? "Добавить профиль" : "Add profile"}
            </button>
          </div>
          <div className="stack-list panel-scroll profile-list">
            {profiles.map((profile) => (
              <div className={cx("scenario-card", profile.id === activeProfile && "active")} key={profile.id}>
                <div>
                  <strong>{profile.name}</strong>
                  <small>{profile.faultProfileEnabled ? `${profile.faultErrorRate}% -> ${profile.faultStatusCode}` : "fault off"} · {profile.latencyMinMs}-{profile.latencyMaxMs}ms</small>
                  <small>{profile.id === activeProfile ? (lang === "ru" ? "активен" : "active") : (lang === "ru" ? "готов к включению" : "ready")}</small>
                </div>
                <div className="card-actions">
                  <button className="soft-button" onClick={() => activateProfile(profile.id)} disabled={!canWrite || profile.id === activeProfile}>{lang === "ru" ? "Включить" : "Activate"}</button>
                  <button className="soft-button" onClick={() => editProfile(profile)} disabled={!canWrite}>{t.save}</button>
                  <button className="icon-button danger-icon" onClick={() => deleteProfile(profile.id)} disabled={!canWrite}><Trash2 size={16} /></button>
                </div>
              </div>
            ))}
            {profiles.length === 0 && <EmptyState icon={<Settings2 size={28} />} text={lang === "ru" ? "Профилей пока нет." : "No profiles yet."} compact />}
          </div>
        </article>

        <article className="surface runtime-panel scenario-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">Scenario editor</p>
              <h2>{lang === "ru" ? "Сценарии ответов" : "Response scenarios"}</h2>
            </div>
            <button className="soft-button" onClick={() => selectedInstance && refreshScenarios(selectedInstance.id)} disabled={!selectedInstance}>
              <RefreshCw size={16} />
              {t.refresh}
            </button>
          </div>
          <p className="hint-text">{lang === "ru"
            ? "Порядок применения: сценарий ответа выше правил полей, затем OpenAPI example или генерация по schema."
            : "Apply order: response scenario wins over field rules, then OpenAPI example or schema generation."}</p>
          <div className="preset-row">
            <button className="soft-button" onClick={() => applyScenarioPreset("success")} disabled={!canWrite}>{lang === "ru" ? "Успешный заказ" : "Success order"}</button>
            <button className="soft-button" onClick={() => applyScenarioPreset("payment-error")} disabled={!canWrite}>{lang === "ru" ? "Ошибка оплаты" : "Payment error"}</button>
            <button className="soft-button" onClick={() => applyScenarioPreset("empty-list")} disabled={!canWrite}>{lang === "ru" ? "Пустой список" : "Empty list"}</button>
            <button className="soft-button" onClick={() => applyScenarioPreset("delay")} disabled={!canWrite}>{lang === "ru" ? "Задержка" : "Delay"}</button>
          </div>
          <div className="scenario-form">
            <label>{lang === "ru" ? "Название" : "Name"}<input value={scenarioName} onChange={(event) => setScenarioName(event.target.value)} disabled={!canWrite} /></label>
            <label>{lang === "ru" ? "Операция" : "Operation"}
              <select value={`${scenarioMethod} ${scenarioPathTemplate}`} onChange={(event) => {
                const [method, ...pathParts] = event.target.value.split(" ");
                const route = contractRoutes.find((item) => item.method === method && item.pathTemplate === pathParts.join(" "));
                setScenarioMethod(method);
                setScenarioPathTemplate(pathParts.join(" "));
                setScenarioOperationId(route?.operationId ?? "");
              }} disabled={!canWrite}>
                <option value={`${scenarioMethod} ${scenarioPathTemplate}`}>{scenarioMethod} {scenarioPathTemplate}</option>
                {contractRoutes.map((route) => (
                  <option value={`${route.method} ${route.pathTemplate}`} key={`${route.method}-${route.pathTemplate}`}>{route.method} {route.pathTemplate}</option>
                ))}
              </select>
            </label>
            <label>{lang === "ru" ? "Статус" : "Status"}<input type="number" min={100} max={599} value={scenarioStatusCode} onChange={(event) => setScenarioStatusCode(Number(event.target.value))} disabled={!canWrite} /></label>
            <label>{lang === "ru" ? "Приоритет" : "Priority"}<input type="number" value={scenarioPriority} onChange={(event) => setScenarioPriority(Number(event.target.value))} disabled={!canWrite} /></label>
            <label>{lang === "ru" ? "Задержка, мс" : "Delay, ms"}<input type="number" min={0} value={scenarioDelayMs} onChange={(event) => setScenarioDelayMs(Number(event.target.value))} disabled={!canWrite} /></label>
            <label className="check-row">
              <input type="checkbox" checked={scenarioEnabled} onChange={(event) => setScenarioEnabled(event.target.checked)} disabled={!canWrite} />
              {lang === "ru" ? "Включён" : "Enabled"}
            </label>
            <label className="scenario-body-field">{lang === "ru" ? "Тело ответа с шаблонами" : "Response body with templates"}
              <textarea className="body-editor" value={scenarioBody} onChange={(event) => setScenarioBody(event.target.value)} disabled={!canWrite} />
            </label>
            <button className="primary-button" onClick={saveScenario} disabled={!selectedInstance || !canWrite || busy["scenario-save"]}>
              {busy["scenario-save"] ? <Loader2 className="spin" size={16} /> : <Play size={16} />}
              {editingScenarioId ? t.save : lang === "ru" ? "Добавить сценарий" : "Add scenario"}
            </button>
          </div>
          <div className="stack-list panel-scroll">
            {scenarios.map((scenario) => (
              <div className="scenario-card" key={scenario.id}>
                <div>
                  <strong>{scenario.name}</strong>
                  <small>{scenario.enabled ? "ON" : "OFF"} · p{scenario.priority} · {scenario.method ?? "-"} {scenario.pathTemplate ?? scenario.operationId ?? "-"}</small>
                  <small>{scenario.statusCode ?? "-"} · {scenario.delayMs}ms</small>
                </div>
                <div className="card-actions">
                  <button className="soft-button" onClick={() => editScenario(scenario)} disabled={!canWrite}>{t.save}</button>
                  <button className="icon-button danger-icon" onClick={() => deleteScenario(scenario.id)} disabled={!canWrite}><Trash2 size={16} /></button>
                </div>
              </div>
            ))}
            {scenarios.length === 0 && <EmptyState icon={<Braces size={28} />} text={lang === "ru" ? "Сценарии пока не настроены." : "No scenarios configured yet."} compact />}
          </div>
        </article>

        <article className="surface runtime-panel smart-response-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">Smart responses</p>
              <h2>{lang === "ru" ? "Правила полей ответа" : "Response field rules"}</h2>
            </div>
            <button className="soft-button" onClick={() => selectedInstance && refreshResponseRules(selectedInstance.id)} disabled={!selectedInstance}>
              <RefreshCw size={16} />
              {t.refresh}
            </button>
          </div>
          <p className="hint-text">{lang === "ru"
            ? "Правила меняют уже выбранный ответ. Шаблоны: {{path.id}}, {{query.name}}, {{header.x-trace-id}}, {{uuid}}."
            : "Rules modify the selected response. Templates: {{path.id}}, {{query.name}}, {{header.x-trace-id}}, {{uuid}}."}</p>
          <div className="preset-row">
            <button className="soft-button" onClick={() => applyRulePreset("random-price")} disabled={!canWrite}>{lang === "ru" ? "Случайная цена" : "Random price"}</button>
            <button className="soft-button" onClick={() => applyRulePreset("template-email")} disabled={!canWrite}>{lang === "ru" ? "Email из запроса" : "Request email"}</button>
          </div>
          <div className="scenario-form response-rule-form">
            <label>{lang === "ru" ? "Название" : "Name"}<input value={ruleName} onChange={(event) => setRuleName(event.target.value)} disabled={!canWrite} /></label>
            <label>{lang === "ru" ? "Операция" : "Operation"}
              <select value={`${ruleMethod} ${rulePathTemplate}`} onChange={(event) => {
                const [method, ...pathParts] = event.target.value.split(" ");
                const pathTemplate = pathParts.join(" ");
                const route = contractRoutes.find((item) => item.method === method && item.pathTemplate === pathTemplate);
                setRuleMethod(method);
                setRulePathTemplate(pathTemplate);
                setRuleOperationId(route?.operationId ?? "");
              }} disabled={!canWrite}>
                <option value={`${ruleMethod} ${rulePathTemplate}`}>{ruleMethod} {rulePathTemplate}</option>
                {contractRoutes.map((route) => (
                  <option value={`${route.method} ${route.pathTemplate}`} key={`rule-${route.method}-${route.pathTemplate}`}>{route.method} {route.pathTemplate}</option>
                ))}
              </select>
            </label>
            <label>JSON path<input value={ruleFieldPath} onChange={(event) => setRuleFieldPath(event.target.value)} placeholder="customer.email" disabled={!canWrite} /></label>
            <label>{lang === "ru" ? "Тип правила" : "Rule type"}
              <select value={ruleType} onChange={(event) => setRuleType(event.target.value)} disabled={!canWrite}>
                <option value="FIXED">{ruleTypeLabel("FIXED", lang)}</option>
                <option value="RANDOM_INT">{ruleTypeLabel("RANDOM_INT", lang)}</option>
                <option value="ENUM">{ruleTypeLabel("ENUM", lang)}</option>
                <option value="TEMPLATE">{ruleTypeLabel("TEMPLATE", lang)}</option>
              </select>
            </label>
            {ruleType === "FIXED" && <label>Value<input value={ruleFixedValue} onChange={(event) => setRuleFixedValue(event.target.value)} disabled={!canWrite} /></label>}
            {ruleType === "RANDOM_INT" && (
              <>
                <label>Min<input type="number" value={ruleMinValue} onChange={(event) => setRuleMinValue(Number(event.target.value))} disabled={!canWrite} /></label>
                <label>Max<input type="number" value={ruleMaxValue} onChange={(event) => setRuleMaxValue(Number(event.target.value))} disabled={!canWrite} /></label>
              </>
            )}
            {ruleType === "ENUM" && <label>Values<input value={ruleEnumValues} onChange={(event) => setRuleEnumValues(event.target.value)} disabled={!canWrite} /></label>}
            {ruleType === "TEMPLATE" && <label>Template<input value={ruleTemplate} onChange={(event) => setRuleTemplate(event.target.value)} disabled={!canWrite} /></label>}
            <label>Priority<input type="number" value={rulePriority} onChange={(event) => setRulePriority(Number(event.target.value))} disabled={!canWrite} /></label>
            <label className="check-row">
              <input type="checkbox" checked={ruleEnabled} onChange={(event) => setRuleEnabled(event.target.checked)} disabled={!canWrite} />
              Enabled
            </label>
            <button className="primary-button" onClick={saveResponseRule} disabled={!selectedInstance || !canWrite || busy["response-rule-save"]}>
              {busy["response-rule-save"] ? <Loader2 className="spin" size={16} /> : <Play size={16} />}
              {editingRuleId ? t.save : lang === "ru" ? "Добавить правило" : "Add rule"}
            </button>
          </div>
          <div className="stack-list panel-scroll">
            {responseRules.map((rule) => (
              <div className="scenario-card" key={rule.id}>
                <div>
                  <strong>{rule.name}</strong>
                  <small>{rule.enabled ? "ON" : "OFF"} В· p{rule.priority} В· {rule.method ?? "-"} {rule.pathTemplate ?? rule.operationId ?? "-"}</small>
                  <small>{rule.fieldPath} · {ruleTypeLabel(rule.type, lang)}</small>
                </div>
                <div className="card-actions">
                  <button className="soft-button" onClick={() => editResponseRule(rule)} disabled={!canWrite}>{t.save}</button>
                  <button className="icon-button danger-icon" onClick={() => deleteResponseRule(rule.id)} disabled={!canWrite}><Trash2 size={16} /></button>
                </div>
              </div>
            ))}
            {responseRules.length === 0 && <EmptyState icon={<Braces size={28} />} text={lang === "ru" ? "Правила ответа пока не настроены." : "No response rules configured yet."} compact />}
          </div>
        </article>

        <article className="surface runtime-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.state}</p>
              <h2>{t.state}</h2>
            </div>
            <button className="soft-button" onClick={() => refreshState()} disabled={!selectedInstance}><Database size={16} />{t.refresh}</button>
          </div>
          <pre className="panel-pre">{JSON.stringify(stateSnapshot, null, 2)}</pre>
        </article>

        <article className="surface runtime-panel runtime-slots-panel" aria-hidden="true">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.runtimePlane}</p>
              <h2>Slots</h2>
            </div>
            <button className="soft-button" onClick={refreshRuntimePlane}><RefreshCw size={16} />{t.refresh}</button>
          </div>
          <div className="stack-list panel-scroll">
            {runtimeSlots.map((slot) => (
              <div className="slot-row" key={slot.instanceId}>
                <Braces size={17} />
                <strong>{slot.mode}</strong>
                <span>{slot.tokenPreview}</span>
                <small>{slot.workerKey ? `${slot.workerKey} · ` : ""}{slot.stateCollectionCount} collections</small>
              </div>
            ))}
            {runtimeSlots.length === 0 && <EmptyState icon={<Server size={28} />} text={t.emptyRuntime} compact />}
          </div>
        </article>
      </section>
    );
  }

  function renderLogs() {
    return (
      <section className="surface logs-panel">
        <div className="surface-heading">
          <div>
            <p className="eyebrow">{t.logs}</p>
            <h2>{t.diagnostics}</h2>
          </div>
          <div className="inline-controls">
            <select value={logMethodFilter} onChange={(event) => setLogMethodFilter(event.target.value)}>
              <option value="ALL">ALL</option>
              <option>GET</option>
              <option>POST</option>
              <option>PUT</option>
              <option>PATCH</option>
              <option>DELETE</option>
            </select>
            <select value={logMatchFilter} onChange={(event) => setLogMatchFilter(event.target.value)}>
              <option value="ALL">ALL</option>
              <option value="MATCHED">{t.matched}</option>
              <option value="UNMATCHED">{t.unmatched}</option>
            </select>
            <select value={logStatusFilter} onChange={(event) => setLogStatusFilter(event.target.value)}>
              <option value="ALL">Status: ALL</option>
              <option value="2xx">2xx</option>
              <option value="4xx">4xx</option>
              <option value="5xx">5xx</option>
            </select>
            <select value={logSourceFilter} onChange={(event) => setLogSourceFilter(event.target.value)}>
              <option value="ALL">{lang === "ru" ? "Источник: ALL" : "Source: ALL"}</option>
              <option value="SCENARIO">{sourceLabel("SCENARIO", lang)}</option>
              <option value="RESPONSE_RULE">{sourceLabel("RESPONSE_RULE", lang)}</option>
              <option value="OPENAPI_EXAMPLE">{sourceLabel("OPENAPI_EXAMPLE", lang)}</option>
              <option value="SCHEMA_GENERATED">{sourceLabel("SCHEMA_GENERATED", lang)}</option>
              <option value="STATEFUL">{sourceLabel("STATEFUL", lang)}</option>
              <option value="FALLBACK">{sourceLabel("FALLBACK", lang)}</option>
            </select>
            <button className="soft-button" onClick={() => refreshLogs()} disabled={!selectedInstance}><RefreshCw size={16} />{t.refresh}</button>
          </div>
        </div>
        <div className="log-table panel-scroll logs-scroll">
          {visibleLogsPage.items.map((log) => (
            <button className="log-row" key={log.id} onClick={() => setSelectedLog(log)}>
              <span className={cx("method-badge", log.method.toLowerCase())}>{log.method}</span>
              <strong>{log.path}</strong>
              <span>{log.responseStatus}</span>
              <span>{log.latencyMs}ms</span>
              <span className="source-chip">{sourceLabel(log.responseSource, lang)}</span>
              <StatusPill ok={log.matched} text={log.matched ? t.matched : t.unmatched} />
            </button>
          ))}
          {visibleLogs.length === 0 && <EmptyState icon={<Code2 size={28} />} text={t.emptyLogs} />}
        </div>
        <Pager
          page={visibleLogsPage.page}
          totalPages={visibleLogsPage.totalPages}
          total={visibleLogsPage.total}
          previousLabel={t.pagePrevious}
          nextLabel={t.pageNext}
          onPage={setLogPage}
        />
      </section>
    );
  }

  function renderAccess() {
    return (
      <section className="access-grid">
        <article className="surface access-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.members}</p>
              <h2>{t.access}</h2>
            </div>
          </div>
          <div className="stack-list panel-scroll">
            {memberListPage.items.map((member) => (
              <div className="member-row" key={member.userId}>
                <Users size={18} />
                <span className="member-identity">
                  <strong>{displayUser(member)}</strong>
                  <small>{member.email}</small>
                </span>
                <RoleBadge role={member.role} />
                {canOwn && member.role !== "OWNER" && (
                  <button className="icon-button danger-icon" onClick={() => openDeleteDialog({ kind: "member", id: member.userId, name: displayUser(member) })}>
                    <Trash2 size={16} />
                  </button>
                )}
              </div>
            ))}
          </div>
          <Pager
            page={memberListPage.page}
            totalPages={memberListPage.totalPages}
            total={memberListPage.total}
            previousLabel={t.pagePrevious}
            nextLabel={t.pageNext}
            onPage={setMemberPage}
          />
        </article>
        <article className="surface access-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.addMember}</p>
              <h2>{t.members}</h2>
            </div>
          </div>
          {!canOwn && <p className="muted">{t.viewerHint}</p>}
          <label>{t.memberUsername}<input value={memberEmail} onChange={(event) => setMemberEmail(event.target.value)} disabled={!canOwn} /></label>
          <label>{t.role}
            <select value={memberRole} onChange={(event) => setMemberRole(event.target.value as ProjectRole)} disabled={!canOwn}>
              <option value="VIEWER">VIEWER</option>
              <option value="MEMBER">MEMBER</option>
            </select>
          </label>
          <button className="primary-button" onClick={addMember} disabled={!canOwn || !memberEmail}>
            <UserPlus size={17} />
            {t.addMember}
          </button>
        </article>
        <article className="surface access-audit overview-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.audit}</p>
              <h2>{t.audit}</h2>
            </div>
          </div>
          <div className="panel-scroll compact-scroll">
            <AuditList items={projectAuditListPage.items} emptyText={t.emptyAudit} />
          </div>
          <Pager
            page={projectAuditListPage.page}
            totalPages={projectAuditListPage.totalPages}
            total={projectAuditListPage.total}
            previousLabel={t.pagePrevious}
            nextLabel={t.pageNext}
            onPage={setProjectAuditPage}
          />
        </article>
      </section>
    );
  }

  function renderSettings() {
    return (
      <section className="settings-grid">
        <article className="surface settings-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.profile}</p>
              <h2>{t.accountSettings}</h2>
            </div>
          </div>
          <div className="detail-grid">
            <Detail label={t.email} value={user?.email ?? "-"} />
            <Detail label={t.role} value={user?.systemRole ?? "USER"} />
            <Detail label="ID" value={user?.id ?? "-"} />
          </div>
          <label>{t.username}<input value={accountUsername} onChange={(event) => setAccountUsername(event.target.value)} /></label>
          <button className="primary-button stable-action" onClick={updateAccountUsername} disabled={busy["account-username"] || !accountUsername.trim()}>
            {busy["account-username"] ? <Loader2 className="spin" size={17} /> : <UserPlus size={17} />}
            {t.changeUsername}
          </button>
        </article>
        <article className="surface settings-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.preferences}</p>
              <h2>{t.settings}</h2>
            </div>
          </div>
          <div className="settings-actions">
            <button className="segmented-button stable-action" onClick={() => setTheme(theme === "light" ? "dark" : "light")}>
              {theme === "light" ? <Moon size={17} /> : <Sun size={17} />}
              {theme === "light" ? t.dark : t.light}
            </button>
            <button className="segmented-button stable-action" onClick={() => setLang(lang === "ru" ? "en" : "ru")}>
              <Globe2 size={17} />
              {lang.toUpperCase()}
            </button>
          </div>
          <p className="muted">{t.language}: {lang.toUpperCase()} · {t.theme}: {theme === "light" ? t.light : t.dark}</p>
        </article>
        <article className="surface settings-panel settings-wide">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.changePassword}</p>
              <h2>{t.session}</h2>
            </div>
          </div>
          <div className="form-grid two">
            <label>{t.currentPassword}<input type="password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} /></label>
            <label>{t.newPassword}<input type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} /></label>
          </div>
          <div className="button-row">
            <button className="primary-button stable-action" onClick={updateAccountPassword} disabled={busy["account-password"] || currentPassword.length < 6 || newPassword.length < 6}>
              {busy["account-password"] ? <Loader2 className="spin" size={17} /> : <KeyRound size={17} />}
              {t.changePassword}
            </button>
            <button className="ghost-button stable-action" onClick={logout}>
              <LogOut size={17} />
              {t.logout}
            </button>
          </div>
        </article>
      </section>
    );
  }

  function renderAdmin() {
    return (
      <section className="admin-grid">
        <article className="surface admin-section-wide">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.admin}</p>
              <h2>{t.adminSummary}</h2>
            </div>
            <button className="soft-button stable-action" onClick={() => refreshAdmin()}>
              {busy.admin ? <Loader2 className="spin" size={16} /> : <RefreshCw size={16} />}
              {t.refresh}
            </button>
          </div>
          <div className="stats-grid">
            <Metric icon={<Users size={19} />} label={t.adminUsers} value={adminSummary?.users ?? 0} />
            <Metric icon={<Layers3 size={19} />} label={t.adminProjects} value={adminSummary?.projects ?? 0} />
            <Metric icon={<Server size={19} />} label={t.adminInstances} value={adminSummary?.instances ?? 0} />
            <Metric icon={<Activity size={19} />} label={t.adminLogs} value={adminSummary?.requestLogs ?? 0} />
          </div>
          {adminSummary && (
            <div className="admin-kpis">
              <Detail label="Invalid specs" value={String(adminSummary.invalidSpecVersions)} />
              <Detail label="Running instances" value={String(adminSummary.runningInstances)} />
              <Detail label="Runtime workers" value={String(adminSummary.runtimeWorkers)} />
              <Detail label="Runtime slots" value={String(adminSummary.runtimeSlots)} />
              <Detail label="5xx errors" value={String(adminSummary.serverErrors)} />
              <Detail label="Unmatched" value={String(adminSummary.unmatchedRequests)} />
              <Detail label="Unmatched ratio" value={`${adminSummary.unmatchedRatio}%`} />
              <Detail label="Rate-limit events" value={String(adminSummary.rateLimitEvents)} />
              <Detail label="Avg latency" value={`${adminSummary.averageLatencyMs}ms`} />
            </div>
          )}
        </article>

        <article className="surface admin-section-wide">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.adminUsers}</p>
              <h2>{t.adminUsers}</h2>
            </div>
            <div className="admin-toolbar compact-toolbar">
              <input value={adminQuery} onChange={(event) => setAdminQuery(event.target.value)} placeholder={t.adminSearch} />
              <button className="soft-button stable-action" onClick={() => refreshAdmin()}>
                {busy.admin ? <Loader2 className="spin" size={16} /> : <RefreshCw size={16} />}
                {t.refresh}
              </button>
            </div>
          </div>
          <div className="admin-table users-table">
            <div className="admin-row admin-head">
              <span>{t.username}</span>
              <span>Email</span>
              <span>Role</span>
              <span>Projects</span>
              <span>Status</span>
              <span>Action</span>
            </div>
            {adminUserListPage.items.map((adminUser) => (
              <div className="admin-row" key={adminUser.id}>
                <strong>{displayUser(adminUser)}</strong>
                <small>{adminUser.email}</small>
                <SystemRoleBadge role={adminUser.systemRole} />
                <span>{adminUser.ownedProjects}</span>
                <StatusPill ok={!adminUser.disabled} text={adminUser.disabled ? t.adminDisabled : t.adminActive} />
                <div className="admin-actions">
                  <button
                    className="soft-button"
                    onClick={() => setUserAdmin(adminUser, adminUser.systemRole !== "ADMIN")}
                    disabled={adminUser.id === user?.id || busy[`admin-role-${adminUser.id}`]}
                  >
                    {adminUser.systemRole === "ADMIN" ? t.adminRevoke : t.adminPromote}
                  </button>
                  <button
                    className={adminUser.disabled ? "soft-button" : "danger-button"}
                    onClick={() => setUserDisabled(adminUser, !adminUser.disabled)}
                    disabled={adminUser.id === user?.id || busy[`admin-user-${adminUser.id}`]}
                  >
                    {adminUser.disabled ? t.adminEnable : t.adminDisable}
                  </button>
                  <button
                    className="icon-button danger-icon"
                    onClick={() => openDeleteDialog({ kind: "admin-user", id: adminUser.id, name: displayUser(adminUser) })}
                    disabled={adminUser.id === user?.id}
                    title={t.adminDeleteUser}
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            ))}
            {adminUsers.length === 0 && <EmptyState icon={<Users size={28} />} text={t.adminUsers} compact />}
          </div>
          <Pager
            page={adminUserListPage.page}
            totalPages={adminUserListPage.totalPages}
            total={adminUserListPage.total}
            previousLabel={t.pagePrevious}
            nextLabel={t.pageNext}
            onPage={setAdminUserPage}
          />
        </article>

        <article className="surface">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.adminProjects}</p>
              <h2>{t.adminProjects}</h2>
            </div>
          </div>
          <div className="admin-table compact-admin-table">
            {adminProjectListPage.items.map((project) => (
              <div className="admin-card-row" key={project.id}>
                <div className="admin-card-title">
                  <strong>{project.name}</strong>
                  <button className="icon-button small danger-icon" onClick={() => openDeleteDialog({ kind: "admin-project", id: project.id, name: project.name })} title={t.deleteProject}>
                    <Trash2 size={15} />
                  </button>
                </div>
                <small>{project.description || "-"}</small>
                <div className="admin-card-meta">
                  <span>{project.specVersionCount} specs</span>
                  <span>{project.instanceCount} instances</span>
                  <span>{project.memberCount} members</span>
                </div>
                <div className="admin-card-description">
                  <span><b>Owner</b><small>{project.ownerUsername || project.ownerEmail}</small></span>
                  <span><b>ID</b><small>{project.id}</small></span>
                  <span><b>Date</b><small>{new Date(project.createdAt).toLocaleString()}</small></span>
                </div>
              </div>
            ))}
            {adminProjects.length === 0 && <EmptyState icon={<Layers3 size={28} />} text={t.adminProjects} compact />}
          </div>
          <Pager
            page={adminProjectListPage.page}
            totalPages={adminProjectListPage.totalPages}
            total={adminProjectListPage.total}
            previousLabel={t.pagePrevious}
            nextLabel={t.pageNext}
            onPage={setAdminProjectPage}
          />
        </article>

        <article className="surface">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.adminInstances}</p>
              <h2>{t.adminInstances}</h2>
            </div>
          </div>
          <div className="admin-table compact-admin-table">
            {adminInstanceListPage.items.map((instance) => (
              <div className="admin-card-row" key={instance.id}>
                <div className="admin-card-title">
                  <strong>{instance.status} · {instance.mode}</strong>
                  <button className="icon-button small danger-icon" onClick={() => openDeleteDialog({ kind: "admin-instance", id: instance.id, name: instance.tokenPreview || instance.id })} title={t.deleteInstance}>
                    <Trash2 size={15} />
                  </button>
                </div>
                <div className="admin-card-meta">
                  <span>{instance.routeCount} routes</span>
                  <span>API key {instance.requireApiKey ? "on" : "off"}</span>
                  <span>{instance.tokenPreview}</span>
                </div>
                <div className="admin-card-description">
                  <span><b>Project</b><small>{instance.projectId}</small></span>
                  <span><b>Spec</b><small>{instance.specVersionId}</small></span>
                  <span><b>Date</b><small>{new Date(instance.createdAt).toLocaleString()}</small></span>
                </div>
              </div>
            ))}
            {adminInstances.length === 0 && <EmptyState icon={<Server size={28} />} text={t.adminInstances} compact />}
          </div>
          <Pager
            page={adminInstanceListPage.page}
            totalPages={adminInstanceListPage.totalPages}
            total={adminInstanceListPage.total}
            previousLabel={t.pagePrevious}
            nextLabel={t.pageNext}
            onPage={setAdminInstancePage}
          />
        </article>

        <article className="surface admin-diagnostics-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.adminLogs}</p>
              <h2>{t.adminLogs}</h2>
            </div>
            <select value={adminLogUserId} onChange={(event) => changeAdminLogUser(event.target.value)}>
              <option value="ALL">{t.adminUsers}: ALL</option>
              {adminUsers.map((adminUser) => (
                <option value={adminUser.id} key={adminUser.id}>{displayUser(adminUser)}</option>
              ))}
            </select>
          </div>
          <div className="log-table admin-scroll-list">
            {adminLogs.map((log) => (
              <div className="log-row" key={log.id}>
                <span className={cx("method-badge", log.method.toLowerCase())}>{log.method}</span>
                <span>
                  <strong>{log.path}</strong>
                  <small>{log.ownerUsername || log.ownerEmail} · {log.projectName}</small>
                </span>
                <span>{log.responseStatus}</span>
                <span>{log.latencyMs}ms</span>
                <span className="source-chip">{sourceLabel(log.responseSource, lang)}</span>
                <StatusPill ok={log.matched} text={log.matched ? t.matched : t.unmatched} />
              </div>
            ))}
            {adminLogs.length === 0 && <EmptyState icon={<Code2 size={28} />} text={t.adminLogs} compact />}
          </div>
          <Pager
            page={adminLogPage}
            totalPages={adminLogTotalPages}
            total={adminLogTotal}
            previousLabel={t.pagePrevious}
            nextLabel={t.pageNext}
            onPage={changeAdminLogPage}
          />
        </article>

        <article className="surface admin-diagnostics-panel">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.adminAudit}</p>
              <h2>{t.adminAudit}</h2>
            </div>
            <select value={adminAuditActorId} onChange={(event) => changeAdminAuditActor(event.target.value)}>
              <option value="ALL">{t.adminActor}: ALL</option>
              {adminUsers.map((adminUser) => (
                <option value={adminUser.id} key={adminUser.id}>{displayUser(adminUser)}</option>
              ))}
            </select>
          </div>
          <div className="admin-scroll-list">
            <AdminAuditList items={adminAudit} emptyText={t.emptyAudit} />
          </div>
          <Pager
            page={adminAuditPage}
            totalPages={adminAuditTotalPages}
            total={adminAuditTotal}
            previousLabel={t.pagePrevious}
            nextLabel={t.pageNext}
            onPage={changeAdminAuditPage}
          />
        </article>

        <article className="surface admin-section-wide admin-runtime-plane">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">{t.adminHealthy}</p>
              <h2>{t.runtimePlane}</h2>
            </div>
          </div>
          <div className="runtime-admin-grid">
            <div className="stack-list panel-scroll compact-scroll">
              {adminWorkers.map((workerItem) => (
                <div className="worker-row" key={workerItem.id ?? workerItem.workerKey}>
                  <span className={cx("status-dot", workerItem.status.toLowerCase())} />
                  <strong>{workerItem.workerKey}</strong>
                  <span>{workerItem.status}</span>
                  <small>{workerItem.slotCount} slots</small>
                </div>
              ))}
            </div>
            <div className="stack-list panel-scroll compact-scroll">
              {adminSlotListPage.items.map((slot) => (
                <div className="slot-row" key={slot.instanceId}>
                  <Braces size={17} />
                  <strong>{slot.mode}</strong>
                  <span>{slot.tokenPreview}</span>
                  <small>{slot.workerKey ? `${slot.workerKey} · ` : ""}{slot.stateCollectionCount} collections</small>
                </div>
              ))}
              <Pager
                page={adminSlotListPage.page}
                totalPages={adminSlotListPage.totalPages}
                total={adminSlotListPage.total}
                previousLabel={t.pagePrevious}
                nextLabel={t.pageNext}
                onPage={setAdminSlotPage}
              />
            </div>
          </div>
        </article>
      </section>
    );
  }
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

function EmptyState({ icon, text, compact = false }: { icon: React.ReactNode; text: string; compact?: boolean }) {
  return (
    <div className={cx("empty-state", compact && "compact")}>
      {icon}
      <span>{text}</span>
    </div>
  );
}

function SkeletonRows({ count }: { count: number }) {
  return (
    <>
      {Array.from({ length: count }).map((_, index) => <div className="skeleton-row" key={index} />)}
    </>
  );
}

function StatusPill({ ok, text }: { ok: boolean; text: string }) {
  return <span className={cx("status-pill", ok ? "success" : "danger")}>{text}</span>;
}

function sourceLabel(source: string | null | undefined, lang: Lang) {
  const labels: Record<string, { ru: string; en: string }> = {
    SCENARIO: { ru: "Сценарий", en: "Scenario" },
    RESPONSE_RULE: { ru: "Правило поля", en: "Field rule" },
    OPENAPI_EXAMPLE: { ru: "OpenAPI example", en: "OpenAPI example" },
    SCHEMA_GENERATED: { ru: "Генерация по схеме", en: "Schema generated" },
    STATEFUL: { ru: "Stateful CRUD", en: "Stateful CRUD" },
    FALLBACK: { ru: "Fallback", en: "Fallback" }
  };
  const item = labels[source ?? "FALLBACK"] ?? labels.FALLBACK;
  return item[lang];
}

function ruleTypeLabel(type: string, lang: Lang) {
  const labels: Record<string, { ru: string; en: string }> = {
    FIXED: { ru: "Фиксированное значение", en: "Fixed value" },
    RANDOM_INT: { ru: "Случайное число", en: "Random integer" },
    ENUM: { ru: "Выбор из списка", en: "Enum choice" },
    TEMPLATE: { ru: "Шаблонная строка", en: "Template string" }
  };
  return (labels[type] ?? labels.FIXED)[lang];
}

function RoleBadge({ role }: { role: ProjectRole }) {
  return (
    <span className={cx("role-badge", role.toLowerCase())}>
      <Lock size={13} />
      {role}
    </span>
  );
}

function SystemRoleBadge({ role }: { role: SystemRole }) {
  return (
    <span className={cx("role-badge", role === "ADMIN" ? "owner" : "viewer")}>
      <ShieldCheck size={13} />
      {role}
    </span>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="detail-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function ResponseBlock({ title, value, clearLabel, onClear }: { title: string; value: string; clearLabel: string; onClear: () => void }) {
  return (
    <div className="response-box">
      <div className="response-heading">
        <span>{title}</span>
        <button className="ghost-button tiny" onClick={onClear}>{clearLabel}</button>
      </div>
      <pre>{value || "-"}</pre>
    </div>
  );
}

function AuditList({ items, emptyText }: { items: AuditEvent[]; emptyText: string }) {
  if (items.length === 0) {
    return <EmptyState icon={<History size={26} />} text={emptyText} compact />;
  }
  return (
    <div className="audit-list">
      {items.map((event) => (
        <div className="audit-row" key={event.id}>
          <History size={16} />
          <span>
            <strong>{event.action}</strong>
            <small>{event.message} · {new Date(event.createdAt).toLocaleString()}</small>
          </span>
        </div>
      ))}
    </div>
  );
}

function AdminAuditList({ items, emptyText }: { items: AdminAudit[]; emptyText: string }) {
  if (items.length === 0) {
    return <EmptyState icon={<History size={26} />} text={emptyText} compact />;
  }
  return (
    <div className="audit-list">
      {items.map((event) => (
        <div className="audit-row" key={event.id}>
          <History size={16} />
          <span>
            <strong>{event.action}</strong>
            <small>{event.message} · {event.actorUsername || event.actorEmail} · {new Date(event.createdAt).toLocaleString()}</small>
          </span>
        </div>
      ))}
    </div>
  );
}

function Pager({
  page,
  totalPages,
  total,
  previousLabel,
  nextLabel,
  onPage
}: {
  page: number;
  totalPages: number;
  total: number;
  previousLabel: string;
  nextLabel: string;
  onPage: (page: number) => void;
}) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <div className="pager">
      <button className="soft-button" disabled={page <= 0} onClick={() => onPage(page - 1)}>{previousLabel}</button>
      <span>{total === 0 ? "0" : `${page + 1}/${Math.max(totalPages, 1)}`} · {total}</span>
      <button className="soft-button" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>{nextLabel}</button>
    </div>
  );
}

function paginate<T>(items: T[], page: number, size: number) {
  const totalPages = Math.max(1, Math.ceil(items.length / size));
  const currentPage = Math.min(Math.max(page, 0), totalPages - 1);
  const start = currentPage * size;
  return {
    items: items.slice(start, start + size),
    page: currentPage,
    total: items.length,
    totalPages
  };
}

function routeKey(route: ContractRoute) {
  return `${route.method} ${route.pathTemplate}`;
}

function displayUser(value: { username?: string | null; email?: string | null }) {
  if (value.username && value.username.trim()) {
    return value.username;
  }
  return value.email ? value.email.split("@", 1)[0] : "unknown";
}

function suggestUsername(identifier: string) {
  const raw = identifier.includes("@") ? identifier.split("@", 1)[0] : identifier;
  const normalized = raw.toLowerCase().replace(/[^a-z0-9_.-]/g, "-").replace(/^[._-]+|[._-]+$/g, "");
  return normalized.length >= 3 ? normalized.slice(0, 28) : "user";
}

function ensurePath(path: string) {
  return path.startsWith("/") ? path : `/${path}`;
}

function parseJsonOrText(value: string): unknown {
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

function formatBody(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }
  return JSON.stringify(value ?? {}, null, 2);
}

function readStoredUser(): User | null {
  const raw = localStorage.getItem("msaas.user");
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as User;
    return { ...parsed, username: parsed.username || displayUser(parsed) };
  } catch {
    localStorage.removeItem("msaas.user");
    return null;
  }
}

function cx(...values: Array<string | false | null | undefined>) {
  return values.filter(Boolean).join(" ");
}

createRoot(document.getElementById("root")!).render(<App />);
