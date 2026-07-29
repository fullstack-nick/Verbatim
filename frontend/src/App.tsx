import {
  AlertTriangle,
  ArrowLeft,
  BookOpenText,
  Check,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Download,
  FileText,
  Languages,
  LoaderCircle,
  MessageSquareText,
  Plus,
  RefreshCw,
  ScanText,
  Settings2,
  Sparkles,
  Upload,
  X,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";

type Project = {
  id: string;
  name: string;
  defaultSourceLocale: string;
  defaultTargetLocale: string;
  ruleSetVersion: number;
  minimumFontScale: number;
  documentCount: number;
};

type DocumentSummary = {
  id: string;
  sourceFilename: string;
  sourceLocale: string;
  targetLocale: string;
  state: string;
  pageCount: number;
  digitalPageCount: number;
  scannedPageCount: number;
  createdAt: string;
};

type Job = {
  id: string;
  revisionId: string;
  state: string;
  currentStage: string;
  progressCurrent: number;
  progressTotal: number;
  errorMessage?: string;
};

type Finding = {
  id: string;
  code: string;
  severity: "ERROR" | "WARNING";
  message: string;
  pageNumber?: number;
};

type Revision = {
  id: string;
  revisionNumber: number;
  state: string;
  downloadUrl?: string;
  approvedAt?: string;
  findings: Finding[];
  usage: {
    inputTokens: number;
    cachedInputTokens: number;
    outputTokens: number;
    reasoningTokens: number;
    durationMillis: number;
  };
};

type Rule = {
  id?: string;
  type: string;
  name: string;
  sourceLocale?: string;
  targetLocale?: string;
  value: Record<string, unknown>;
};

type RuleSet = {
  version: number;
  minimumFontScale: number;
  rules: Rule[];
};

type Term = {
  id: string;
  sourceTerm: string;
  matchingType: string;
  caseMode: string;
  translationPreference: string;
  translations: Array<{ locale: string; text: string; usage: string }>;
};

const languages = [
  ["en-US", "English (US)"],
  ["de-DE", "German"],
  ["fr-FR", "French"],
  ["es-ES", "Spanish"],
  ["it-IT", "Italian"],
  ["pt-BR", "Portuguese (Brazil)"],
  ["nl-NL", "Dutch"],
  ["pl-PL", "Polish"],
  ["cs-CZ", "Czech"],
  ["el-GR", "Greek"],
  ["uk-UA", "Ukrainian"],
  ["ru-RU", "Russian"],
  ["bg-BG", "Bulgarian"],
  ["sr-RS", "Serbian"],
];

const stageLabels: Record<string, string> = {
  QUEUED: "Waiting for a worker",
  LOADING_CONTEXT: "Loading project context",
  EXTRACTING_SCANNED_TEXT: "Reading scanned pages with vision",
  TRANSLATING: "Translating with Codex",
  DETERMINISTIC_REVIEW: "Checking terms and placeholders",
  COMPOSING_PDF: "Rebuilding the PDF",
  VISUAL_REVIEW: "Comparing source and translated pages",
  FINALIZING: "Finishing the review",
  QA_PASSED: "Ready for approval",
  QA_FLAGGED: "Ready with findings",
  RETRY_SCHEDULED: "Retrying a technical failure",
  FAILED: "Translation failed",
};

async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, options);
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.message ?? `Request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}

function stateTone(state: string) {
  if (state === "APPROVED" || state === "QA_PASSED") return "success";
  if (state === "QA_FLAGGED" || state === "FAILED") return "warning";
  if (state === "QUEUED" || state === "TRANSLATING") return "active";
  return "neutral";
}

function formatTokens(value: number) {
  return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(value);
}

function App() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [activeProject, setActiveProject] = useState<Project | null>(null);
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [activeDocument, setActiveDocument] = useState<DocumentSummary | null>(null);
  const [revisions, setRevisions] = useState<Revision[]>([]);
  const [job, setJob] = useState<Job | null>(null);
  const [view, setView] = useState<"document" | "rules">("document");
  const [showCreate, setShowCreate] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const loadProjects = useCallback(async () => {
    const loaded = await api<Project[]>("/api/projects");
    setProjects(loaded);
    setActiveProject((current) => current
      ? loaded.find((project) => project.id === current.id) ?? loaded[0] ?? null
      : loaded[0] ?? null);
  }, []);

  const loadDocuments = useCallback(async (project: Project) => {
    const loaded = await api<DocumentSummary[]>(`/api/projects/${project.id}/documents`);
    setDocuments(loaded);
    setActiveDocument((current) =>
      current ? loaded.find((document) => document.id === current.id) ?? null : null
    );
  }, []);

  const loadRevisions = useCallback(async (projectId: string, documentId: string) => {
    const loaded = await api<Revision[]>(
      `/api/projects/${projectId}/documents/${documentId}/revisions`,
    );
    setRevisions(loaded);
  }, []);

  useEffect(() => {
    loadProjects().catch((reason) => setError(reason.message));
  }, [loadProjects]);

  useEffect(() => {
    if (!activeProject) {
      setDocuments([]);
      return;
    }
    loadDocuments(activeProject).catch((reason) => setError(reason.message));
  }, [activeProject, loadDocuments]);

  useEffect(() => {
    if (!activeProject || !activeDocument) {
      setRevisions([]);
      return;
    }
    loadRevisions(activeProject.id, activeDocument.id).catch((reason) => setError(reason.message));
  }, [activeProject, activeDocument, loadRevisions]);

  useEffect(() => {
    if (!job || !activeProject || !activeDocument || ["COMPLETED", "FAILED"].includes(job.state)) {
      return;
    }
    const timer = window.setInterval(async () => {
      try {
        const next = await api<Job>(
          `/api/projects/${activeProject.id}/documents/${activeDocument.id}/jobs/${job.id}`,
        );
        setJob(next);
        if (["COMPLETED", "FAILED"].includes(next.state)) {
          await Promise.all([
            loadDocuments(activeProject),
            loadRevisions(activeProject.id, activeDocument.id),
          ]);
        }
      } catch (reason) {
        setError(reason instanceof Error ? reason.message : "Progress could not be loaded.");
      }
    }, 1200);
    return () => window.clearInterval(timer);
  }, [job, activeProject, activeDocument, loadDocuments, loadRevisions]);

  async function createProject(name: string, source: string, target: string) {
    setBusy(true);
    setError("");
    try {
      const created = await api<Project>("/api/projects", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name,
          defaultSourceLocale: source,
          defaultTargetLocale: target,
          minimumFontScale: 0.78,
        }),
      });
      await loadProjects();
      setActiveProject(created);
      setShowCreate(false);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Project could not be created.");
    } finally {
      setBusy(false);
    }
  }

  async function uploadDocument(file: File, source: string, target: string) {
    if (!activeProject) return;
    setBusy(true);
    setError("");
    try {
      const form = new FormData();
      form.append("file", file);
      const uploaded = await api<{ document: DocumentSummary }>(
        `/api/projects/${activeProject.id}/documents?sourceLocale=${encodeURIComponent(source)}&targetLocale=${encodeURIComponent(target)}`,
        { method: "POST", body: form },
      );
      await Promise.all([loadProjects(), loadDocuments(activeProject)]);
      setActiveDocument(uploaded.document);
      setView("document");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "PDF could not be uploaded.");
    } finally {
      setBusy(false);
    }
  }

  async function startTranslation(instruction: string, promote: boolean) {
    if (!activeProject || !activeDocument) return;
    setBusy(true);
    setError("");
    try {
      if (instruction.trim() && promote) {
        await api(`/api/projects/${activeProject.id}/documents/${activeDocument.id}/instructions`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ message: instruction.trim(), promoteToProject: true }),
        });
      }
      const started = await api<{ jobId: string; revisionId: string; state: string }>(
        `/api/projects/${activeProject.id}/documents/${activeDocument.id}/translations`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Idempotency-Key": crypto.randomUUID(),
          },
          body: JSON.stringify({ instructions: instruction.trim() && !promote ? [instruction.trim()] : [] }),
        },
      );
      setJob({
        id: started.jobId,
        revisionId: started.revisionId,
        state: started.state,
        currentStage: "QUEUED",
        progressCurrent: 0,
        progressTotal: 6,
      });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Translation could not be started.");
    } finally {
      setBusy(false);
    }
  }

  async function approveRevision(revision: Revision) {
    if (!activeProject || !activeDocument) return;
    setBusy(true);
    try {
      await api(
        `/api/projects/${activeProject.id}/documents/${activeDocument.id}/revisions/${revision.id}/approve`,
        { method: "POST" },
      );
      await Promise.all([
        loadDocuments(activeProject),
        loadRevisions(activeProject.id, activeDocument.id),
      ]);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Revision could not be approved.");
    } finally {
      setBusy(false);
    }
  }

  const currentRevision = revisions[0];

  if (!projects.length && !showCreate) {
    return (
      <>
        <Landing onCreate={() => setShowCreate(true)} />
        {error && <Toast message={error} onClose={() => setError("")} />}
      </>
    );
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <button className="brand" onClick={() => setActiveDocument(null)}>
          <span className="brand-mark"><Languages size={19} /></span>
          <span>
            <strong>Verbatim</strong>
            <small>Document translation, kept in form</small>
          </span>
        </button>
        <div className="topbar-project">
          <span className="eyebrow">Current project</span>
          <select
            value={activeProject?.id ?? ""}
            onChange={(event) => {
              setActiveProject(projects.find((project) => project.id === event.target.value) ?? null);
              setActiveDocument(null);
            }}
          >
            {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
          </select>
        </div>
        <div className="topbar-actions">
          <button className="soft-button" onClick={() => setShowCreate(true)}>
            <Plus size={16} /> New project
          </button>
          <button
            className={view === "rules" ? "icon-button active" : "icon-button"}
            onClick={() => setView(view === "rules" ? "document" : "rules")}
            aria-label="Project controls"
          >
            <Settings2 size={18} />
          </button>
        </div>
      </header>

      <div className="workspace">
        <aside className="sidebar">
          <div className="sidebar-heading">
            <div>
              <span className="eyebrow">Library</span>
              <h2>Documents</h2>
            </div>
            <UploadButton compact busy={busy} project={activeProject} onUpload={uploadDocument} />
          </div>
          <div className="document-list">
            {documents.map((document) => (
              <button
                key={document.id}
                className={activeDocument?.id === document.id ? "document-item active" : "document-item"}
                onClick={() => {
                  setActiveDocument(document);
                  setView("document");
                  setJob(null);
                }}
              >
                <span className="document-icon">
                  {document.scannedPageCount ? <ScanText size={18} /> : <FileText size={18} />}
                </span>
                <span className="document-copy">
                  <strong>{document.sourceFilename}</strong>
                  <small>{document.sourceLocale} → {document.targetLocale} · {document.pageCount} page{document.pageCount === 1 ? "" : "s"}</small>
                </span>
                <span className={`state-dot ${stateTone(document.state)}`} />
              </button>
            ))}
            {!documents.length && (
              <div className="empty-sidebar">
                <FileText size={20} />
                <p>Your PDFs will live here.</p>
              </div>
            )}
          </div>
          <div className="sidebar-foot">
            <div className="local-chip"><span /> Local workspace</div>
            <small>PostgreSQL is the source of truth</small>
          </div>
        </aside>

        <section className="content">
          {view === "rules" && activeProject ? (
            <RulesPanel
              project={activeProject}
              onChanged={async () => {
                await loadProjects();
                const refreshed = (await api<Project[]>("/api/projects"))
                  .find((project) => project.id === activeProject.id);
                if (refreshed) setActiveProject(refreshed);
              }}
            />
          ) : activeDocument && activeProject ? (
            <DocumentWorkspace
              project={activeProject}
              document={activeDocument}
              revision={currentRevision}
              revisions={revisions}
              job={job}
              busy={busy}
              onBack={() => setActiveDocument(null)}
              onTranslate={startTranslation}
              onApprove={approveRevision}
            />
          ) : activeProject ? (
            <ProjectEmpty project={activeProject} busy={busy} onUpload={uploadDocument} />
          ) : null}
        </section>
      </div>

      {showCreate && (
        <CreateProjectModal
          busy={busy}
          onClose={() => setShowCreate(false)}
          onCreate={createProject}
        />
      )}
      {error && <Toast message={error} onClose={() => setError("")} />}
    </main>
  );
}

function Landing({ onCreate }: { onCreate: () => void }) {
  return (
    <main className="landing">
      <header className="landing-nav">
        <div className="brand">
          <span className="brand-mark"><Languages size={19} /></span>
          <span><strong>Verbatim</strong><small>Local-first PDF translation</small></span>
        </div>
        <span className="local-chip"><span /> Runs on your machine</span>
      </header>
      <section className="landing-grid">
        <div className="hero">
          <div className="hero-kicker"><Sparkles size={16} /> Project-aware PDF translation</div>
          <h1>Every word translated.<br />Every detail intact.</h1>
          <p>
            Translate digital and scanned documents with Codex while preserving page count,
            typography, tables, spacing, and the project language behind them.
          </p>
          <button className="primary-button yellow" onClick={onCreate}>
            <Plus size={18} /> Start a project
          </button>
          <div className="hero-proof">
            <span><Check size={14} /> Latin, Cyrillic & Greek</span>
            <span><Check size={14} /> Visible token usage</span>
            <span><Check size={14} /> Human-approved memory</span>
          </div>
        </div>
        <div className="landing-cards">
          <article className="feature-card lilac">
            <ScanText size={29} />
            <div><h2>Digital or scanned</h2><p>Native extraction first, Codex vision when the page is an image.</p></div>
          </article>
          <article className="feature-card gold">
            <BookOpenText size={29} />
            <div><h2>Rules stay visible</h2><p>Terms and instructions are never hidden inside an unknowable prompt.</p></div>
          </article>
        </div>
      </section>
    </main>
  );
}

function ProjectEmpty({
  project,
  busy,
  onUpload,
}: {
  project: Project;
  busy: boolean;
  onUpload: (file: File, source: string, target: string) => Promise<void>;
}) {
  return (
    <div className="empty-project">
      <div className="empty-orbit"><FileText size={34} /></div>
      <span className="eyebrow">Project ready</span>
      <h1>Bring in the first document</h1>
      <p>
        Verbatim will identify digital and scanned pages, preserve the page count, and prepare
        a translation task using this project’s rules.
      </p>
      <UploadButton project={project} busy={busy} onUpload={onUpload} />
      <div className="acceptance-row">
        <span>PDF up to 250 MB</span><i /> <span>Printed text only</span><i /> <span>No handwriting</span>
      </div>
    </div>
  );
}

function UploadButton({
  project,
  busy,
  compact = false,
  onUpload,
}: {
  project: Project | null;
  busy: boolean;
  compact?: boolean;
  onUpload: (file: File, source: string, target: string) => Promise<void>;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [source, setSource] = useState(project?.defaultSourceLocale ?? "en-US");
  const [target, setTarget] = useState(project?.defaultTargetLocale ?? "de-DE");
  const [choosing, setChoosing] = useState(false);

  useEffect(() => {
    setSource(project?.defaultSourceLocale ?? "en-US");
    setTarget(project?.defaultTargetLocale ?? "de-DE");
  }, [project]);

  if (compact) {
    return (
      <>
        <button className="round-add" onClick={() => setChoosing(true)} aria-label="Upload PDF">
          <Plus size={18} />
        </button>
        {choosing && (
          <div className="popover">
            <button className="popover-close" onClick={() => setChoosing(false)}><X size={16} /></button>
            <strong>New document</strong>
            <LanguageFields source={source} target={target} setSource={setSource} setTarget={setTarget} />
            <button className="primary-button" onClick={() => input.current?.click()} disabled={busy}>
              <Upload size={16} /> Choose PDF
            </button>
            <input
              ref={input}
              hidden
              type="file"
              accept="application/pdf"
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) {
                  onUpload(file, source, target).then(() => setChoosing(false));
                }
              }}
            />
          </div>
        )}
      </>
    );
  }

  return (
    <div className="upload-block">
      <LanguageFields source={source} target={target} setSource={setSource} setTarget={setTarget} />
      <button className="primary-button" onClick={() => input.current?.click()} disabled={busy}>
        {busy ? <LoaderCircle className="spin" size={18} /> : <Upload size={18} />}
        {busy ? "Reading PDF…" : "Choose a PDF"}
      </button>
      <input
        ref={input}
        hidden
        type="file"
        accept="application/pdf"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) onUpload(file, source, target);
        }}
      />
    </div>
  );
}

function LanguageFields({
  source,
  target,
  setSource,
  setTarget,
}: {
  source: string;
  target: string;
  setSource: (value: string) => void;
  setTarget: (value: string) => void;
}) {
  return (
    <div className="language-fields">
      <label><span>From</span><select value={source} onChange={(event) => setSource(event.target.value)}>
        {languages.map(([code, label]) => <option key={code} value={code}>{label}</option>)}
      </select></label>
      <ChevronRight size={16} />
      <label><span>To</span><select value={target} onChange={(event) => setTarget(event.target.value)}>
        {languages.map(([code, label]) => <option key={code} value={code}>{label}</option>)}
      </select></label>
    </div>
  );
}

function DocumentWorkspace({
  project,
  document,
  revision,
  revisions,
  job,
  busy,
  onBack,
  onTranslate,
  onApprove,
}: {
  project: Project;
  document: DocumentSummary;
  revision?: Revision;
  revisions: Revision[];
  job: Job | null;
  busy: boolean;
  onBack: () => void;
  onTranslate: (instruction: string, promote: boolean) => Promise<void>;
  onApprove: (revision: Revision) => Promise<void>;
}) {
  const [instruction, setInstruction] = useState("");
  const [promote, setPromote] = useState(false);
  const [tab, setTab] = useState<"preview" | "findings" | "history">("preview");
  const working = job && !["COMPLETED", "FAILED"].includes(job.state);
  const sourceUrl = `/api/projects/${project.id}/documents/${document.id}/source`;
  const translatedUrl = revision?.downloadUrl;

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onTranslate(instruction, promote);
    setInstruction("");
    setPromote(false);
  }

  return (
    <div className="document-workspace">
      <div className="document-header">
        <button className="icon-button mobile-back" onClick={onBack}><ArrowLeft size={18} /></button>
        <div>
          <div className="document-title-row">
            <h1>{document.sourceFilename}</h1>
            <span className={`status-pill ${stateTone(job?.currentStage ?? revision?.state ?? document.state)}`}>
              {working && <LoaderCircle size={13} className="spin" />}
              {(job?.currentStage ?? revision?.state ?? document.state).replaceAll("_", " ")}
            </span>
          </div>
          <p>{document.sourceLocale} → {document.targetLocale} · {document.pageCount} page{document.pageCount === 1 ? "" : "s"} · {document.scannedPageCount ? "Scanned PDF" : "Digital PDF"}</p>
        </div>
        <div className="document-actions">
          {translatedUrl && (
            <a className="soft-button" href={translatedUrl} download>
              <Download size={16} /> Export PDF
            </a>
          )}
          {revision && !revision.approvedAt && (
            <button className="primary-button compact" onClick={() => onApprove(revision)} disabled={busy || !!working}>
              <CheckCircle2 size={16} /> Approve
            </button>
          )}
        </div>
      </div>

      {working && job && <ProgressBanner job={job} document={document} />}

      <div className="workspace-tabs">
        {(["preview", "findings", "history"] as const).map((item) => (
          <button key={item} className={tab === item ? "active" : ""} onClick={() => setTab(item)}>
            {item === "findings" && revision?.findings.length ? (
              <span className="tab-count">{revision.findings.length}</span>
            ) : null}
            {item[0].toUpperCase() + item.slice(1)}
          </button>
        ))}
      </div>

      {tab === "preview" && (
        <div className="preview-grid">
          <PdfPane title="Original" subtitle={document.sourceLocale} url={sourceUrl} />
          <PdfPane
            title={`Translation${revision ? ` · revision ${revision.revisionNumber}` : ""}`}
            subtitle={document.targetLocale}
            url={translatedUrl}
            pending={!!working}
          />
        </div>
      )}

      {tab === "findings" && <FindingsPanel revision={revision} />}
      {tab === "history" && <RevisionHistory revisions={revisions} />}

      <form className="instruction-bar" onSubmit={submit}>
        <span className="chat-icon"><MessageSquareText size={18} /></span>
        <div className="instruction-copy">
          <input
            value={instruction}
            onChange={(event) => setInstruction(event.target.value)}
            placeholder={revision ? "Ask for a change, then create a new revision…" : "Optional instruction for this document…"}
          />
          <label>
            <input type="checkbox" checked={promote} onChange={(event) => setPromote(event.target.checked)} />
            Save as a project rule
          </label>
        </div>
        <button className="primary-button compact" disabled={busy || !!working}>
          {revision ? <RefreshCw size={16} /> : <Sparkles size={16} />}
          {revision ? "New revision" : "Translate"}
        </button>
      </form>

      {revision && (
        <div className="usage-strip">
          <span><Clock3 size={14} /> {(revision.usage.durationMillis / 1000).toFixed(1)} seconds</span>
          <span>{formatTokens(revision.usage.inputTokens)} input tokens</span>
          <span>{formatTokens(revision.usage.cachedInputTokens)} cached</span>
          <span>{formatTokens(revision.usage.outputTokens)} output</span>
          <small>OCR and translation are counted together for this revision.</small>
        </div>
      )}
    </div>
  );
}

function ProgressBanner({ job, document }: { job: Job; document: DocumentSummary }) {
  const progress = Math.round((job.progressCurrent / Math.max(1, job.progressTotal)) * 100);
  return (
    <div className="progress-banner">
      <span className="progress-icon"><LoaderCircle className="spin" size={19} /></span>
      <div>
        <strong>{stageLabels[job.currentStage] ?? job.currentStage}</strong>
        <small>{document.pageCount > 20 ? "Large documents are processed as bounded page batches." : "The page count will remain unchanged."}</small>
      </div>
      <div className="progress-track"><span style={{ width: `${progress}%` }} /></div>
      <b>{progress}%</b>
    </div>
  );
}

function PdfPane({
  title,
  subtitle,
  url,
  pending,
}: {
  title: string;
  subtitle: string;
  url?: string;
  pending?: boolean;
}) {
  return (
    <section className="pdf-pane">
      <header><div><strong>{title}</strong><span>{subtitle}</span></div></header>
      <div className="pdf-canvas">
        {url ? (
          <iframe title={title} src={`${url}#toolbar=0&navpanes=0&view=FitH`} />
        ) : (
          <div className="pdf-placeholder">
            {pending ? <LoaderCircle className="spin" size={28} /> : <Languages size={28} />}
            <strong>{pending ? "Building your translated PDF" : "No translation yet"}</strong>
            <p>{pending ? "This view will update when review is complete." : "Add an instruction if you like, then start the first revision."}</p>
          </div>
        )}
      </div>
    </section>
  );
}

function FindingsPanel({ revision }: { revision?: Revision }) {
  if (!revision) return <PanelEmpty icon={<AlertTriangle />} title="No review yet" text="Translate the document to produce structured findings." />;
  if (!revision.findings.length) return <PanelEmpty icon={<CheckCircle2 />} title="No findings" text="The deterministic and layout checks passed." />;
  return (
    <div className="findings-panel">
      <div className="findings-summary">
        <span className="finding-orb"><AlertTriangle size={22} /></span>
        <div><h2>{revision.findings.length} item{revision.findings.length === 1 ? "" : "s"} need attention</h2><p>Nothing is hidden: every compromise remains attached to this revision.</p></div>
      </div>
      <div className="finding-list">
        {revision.findings.map((finding) => (
          <article key={finding.id} className={`finding ${finding.severity.toLowerCase()}`}>
            <span>{finding.severity === "ERROR" ? <AlertTriangle size={17} /> : <Clock3 size={17} />}</span>
            <div><strong>{finding.code.replaceAll("_", " ")}</strong><p>{finding.message}</p></div>
            {finding.pageNumber && <small>Page {finding.pageNumber}</small>}
          </article>
        ))}
      </div>
    </div>
  );
}

function RevisionHistory({ revisions }: { revisions: Revision[] }) {
  if (!revisions.length) return <PanelEmpty icon={<Clock3 />} title="No revisions yet" text="Each translation run appears here with its own PDF and usage." />;
  return (
    <div className="revision-list">
      {revisions.map((revision) => (
        <article key={revision.id}>
          <span className={`revision-number ${stateTone(revision.state)}`}>{revision.revisionNumber}</span>
          <div><strong>Revision {revision.revisionNumber}</strong><p>{revision.state.replaceAll("_", " ")} · {revision.findings.length} findings</p></div>
          <span>{formatTokens(revision.usage.inputTokens + revision.usage.outputTokens)} tokens</span>
          {revision.downloadUrl && <a className="icon-button" href={revision.downloadUrl}><Download size={17} /></a>}
        </article>
      ))}
    </div>
  );
}

function PanelEmpty({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return <div className="panel-empty"><span>{icon}</span><h2>{title}</h2><p>{text}</p></div>;
}

function RulesPanel({ project, onChanged }: { project: Project; onChanged: () => Promise<void> }) {
  const [rules, setRules] = useState<RuleSet | null>(null);
  const [terms, setTerms] = useState<Term[]>([]);
  const [instruction, setInstruction] = useState("");
  const [minimumScale, setMinimumScale] = useState(String(project.minimumFontScale));
  const [termSource, setTermSource] = useState("");
  const [termTarget, setTermTarget] = useState("");
  const [neverTranslate, setNeverTranslate] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    const [loadedRules, loadedTerms] = await Promise.all([
      api<RuleSet>(`/api/projects/${project.id}/rules`),
      api<Term[]>(`/api/projects/${project.id}/terms`),
    ]);
    setRules(loadedRules);
    setTerms(loadedTerms);
    setMinimumScale(String(loadedRules.minimumFontScale));
    const activeInstruction = loadedRules.rules.find((rule) => rule.type === "TRANSLATION_INSTRUCTION");
    setInstruction(String(activeInstruction?.value?.instruction ?? ""));
  }, [project.id]);

  useEffect(() => { load().catch((reason) => setMessage(reason.message)); }, [load]);

  async function saveRules(event: FormEvent) {
    event.preventDefault();
    if (!rules) return;
    setSaving(true);
    try {
      const preserved = rules.rules.filter((rule) => rule.type !== "TRANSLATION_INSTRUCTION");
      if (instruction.trim()) {
        preserved.push({
          type: "TRANSLATION_INSTRUCTION",
          name: "Project translation guidance",
          sourceLocale: project.defaultSourceLocale,
          targetLocale: project.defaultTargetLocale,
          value: { instruction: instruction.trim() },
        });
      }
      await api(`/api/projects/${project.id}/rules`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ rules: preserved, minimumFontScale: Number(minimumScale) }),
      });
      await Promise.all([load(), onChanged()]);
      setMessage("Project guidance saved. Future revisions will use the new version.");
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : "Rules could not be saved.");
    } finally {
      setSaving(false);
    }
  }

  async function addTerm(event: FormEvent) {
    event.preventDefault();
    if (!termSource.trim()) return;
    setSaving(true);
    try {
      await api(`/api/projects/${project.id}/terms`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          sourceLocale: project.defaultSourceLocale,
          sourceTerm: termSource.trim(),
          matchingType: "EXACT",
          caseMode: "SENSITIVE",
          translationPreference: neverTranslate ? "NEVER_TRANSLATE" : "TRANSLATE",
          translations: [{
            locale: project.defaultTargetLocale,
            text: neverTranslate ? termSource.trim() : termTarget.trim(),
            usage: "PREFERRED",
          }],
        }),
      });
      setTermSource("");
      setTermTarget("");
      setNeverTranslate(false);
      await Promise.all([load(), onChanged()]);
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : "Term could not be added.");
    } finally {
      setSaving(false);
    }
  }

  async function removeTerm(term: Term) {
    await api(`/api/projects/${project.id}/terms/${term.id}`, { method: "DELETE" });
    await Promise.all([load(), onChanged()]);
  }

  return (
    <div className="rules-panel">
      <div className="rules-hero">
        <div><span className="eyebrow">Translation controls</span><h1>Nothing hidden in the prompt</h1><p>These project-level choices apply to future revisions. Chat instructions remain document-only unless deliberately promoted.</p></div>
        <span className="version-chip">Rule set v{rules?.version ?? project.ruleSetVersion}</span>
      </div>
      <div className="rules-grid">
        <form className="settings-card" onSubmit={saveRules}>
          <div className="settings-card-title"><span className="card-icon green"><MessageSquareText size={19} /></span><div><h2>Project guidance</h2><p>Tone, audience, and recurring translation choices.</p></div></div>
          <label className="field"><span>Instruction</span><textarea rows={5} value={instruction} onChange={(event) => setInstruction(event.target.value)} placeholder="Use a clear, friendly product-documentation tone." /></label>
          <label className="field"><span>Minimum readable font scale</span><div className="range-row"><input type="range" min=".5" max="1" step=".01" value={minimumScale} onChange={(event) => setMinimumScale(event.target.value)} /><b>{Math.round(Number(minimumScale) * 100)}%</b></div><small>Below this threshold, Verbatim flags overflow instead of shrinking further.</small></label>
          <button className="primary-button compact" disabled={saving}>{saving ? <LoaderCircle className="spin" size={16} /> : <Check size={16} />} Save guidance</button>
        </form>

        <section className="settings-card">
          <div className="settings-card-title"><span className="card-icon lilac"><BookOpenText size={19} /></span><div><h2>Terminology</h2><p>Preferred words and names that must remain unchanged.</p></div></div>
          <form className="term-form" onSubmit={addTerm}>
            <input value={termSource} onChange={(event) => setTermSource(event.target.value)} placeholder="Source term" required />
            <input value={termTarget} onChange={(event) => setTermTarget(event.target.value)} placeholder={neverTranslate ? "Same as source" : "Preferred translation"} disabled={neverTranslate} required={!neverTranslate} />
            <label><input type="checkbox" checked={neverTranslate} onChange={(event) => setNeverTranslate(event.target.checked)} /> Never translate</label>
            <button className="round-add" disabled={saving}><Plus size={17} /></button>
          </form>
          <div className="term-list">
            {terms.map((term) => (
              <article key={term.id}>
                <div><strong>{term.sourceTerm}</strong><small>{term.translationPreference === "NEVER_TRANSLATE" ? "Never translate" : term.translations[0]?.text ?? "No target"} · {term.matchingType.toLowerCase()}</small></div>
                <button className="icon-button" onClick={() => removeTerm(term)} aria-label={`Remove ${term.sourceTerm}`}><X size={15} /></button>
              </article>
            ))}
            {!terms.length && <p className="quiet">No terms yet. Add only terminology that should be authoritative.</p>}
          </div>
        </section>
      </div>
      {message && <div className="inline-message">{message}</div>}
    </div>
  );
}

function CreateProjectModal({
  busy,
  onClose,
  onCreate,
}: {
  busy: boolean;
  onClose: () => void;
  onCreate: (name: string, source: string, target: string) => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [source, setSource] = useState("en-US");
  const [target, setTarget] = useState("de-DE");
  return (
    <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <form className="modal" onSubmit={(event) => { event.preventDefault(); onCreate(name, source, target); }}>
        <button type="button" className="modal-close" onClick={onClose}><X size={18} /></button>
        <span className="modal-icon"><Languages size={23} /></span>
        <span className="eyebrow">New project</span>
        <h1>Give every document shared context</h1>
        <p>Terminology, approved memory, and project instructions stay isolated here.</p>
        <label className="field"><span>Project name</span><input autoFocus value={name} onChange={(event) => setName(event.target.value)} placeholder="Product documentation" required /></label>
        <LanguageFields source={source} target={target} setSource={setSource} setTarget={setTarget} />
        <button className="primary-button" disabled={busy}>{busy ? <LoaderCircle className="spin" size={18} /> : <ArrowLeft className="arrow-forward" size={18} />} Create project</button>
      </form>
    </div>
  );
}

function Toast({ message, onClose }: { message: string; onClose: () => void }) {
  return <div className="toast"><AlertTriangle size={17} /><span>{message}</span><button onClick={onClose}><X size={15} /></button></div>;
}

export default App;
