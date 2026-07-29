import {
  ArrowRight,
  BookOpenText,
  FileCheck2,
  FileText,
  Languages,
  Plus,
  ScanText,
  Settings2,
  Sparkles,
} from "lucide-react";

const projects = [
  {
    name: "Product documentation",
    pair: "English to German",
    documents: 12,
    status: "3 ready to review",
    tone: "Friendly and concise",
  },
  {
    name: "Customer handbooks",
    pair: "English to Greek",
    documents: 7,
    status: "All approved",
    tone: "Clear and professional",
  },
];

function App() {
  return (
    <main className="min-h-screen bg-[#f5f2ea] text-[#15211b]">
      <div className="mx-auto max-w-[1500px] px-5 py-5 lg:px-8">
        <header className="flex items-center justify-between rounded-[28px] border border-black/6 bg-white/75 px-5 py-4 shadow-[0_18px_55px_rgba(44,42,35,0.06)] backdrop-blur-xl">
          <div className="flex items-center gap-3">
            <div className="grid size-10 place-items-center rounded-2xl bg-[#173f33] text-white">
              <Languages size={20} strokeWidth={1.8} />
            </div>
            <div>
              <p className="font-display text-xl leading-none">Verbatim</p>
              <p className="mt-1 text-xs text-[#718078]">Document translation, kept in form</p>
            </div>
          </div>
          <nav className="hidden items-center gap-2 rounded-full bg-[#eeece5] p-1 md:flex">
            <button className="rounded-full bg-white px-4 py-2 text-sm shadow-sm">Projects</button>
            <button className="rounded-full px-4 py-2 text-sm text-[#647068]">Activity</button>
          </nav>
          <button className="grid size-10 place-items-center rounded-full border border-black/8 bg-white text-[#415048]">
            <Settings2 size={18} />
          </button>
        </header>

        <section className="grid gap-6 py-8 lg:grid-cols-[1.35fr_0.65fr]">
          <div className="overflow-hidden rounded-[36px] bg-[#173f33] p-8 text-[#f7f4ec] shadow-[0_28px_90px_rgba(23,63,51,0.2)] lg:p-12">
            <div className="flex items-center gap-2 text-sm text-[#b8d6ca]">
              <Sparkles size={16} />
              <span>Project-aware PDF translation</span>
            </div>
            <h1 className="font-display mt-8 max-w-3xl text-5xl leading-[0.98] tracking-[-0.04em] lg:text-7xl">
              Every word translated.
              <br />
              Every detail intact.
            </h1>
            <p className="mt-7 max-w-xl text-base leading-7 text-[#c8d9d2] lg:text-lg">
              Translate digital and scanned documents while preserving the page, typography,
              spacing, and visual rhythm you started with.
            </p>
            <button className="mt-10 inline-flex items-center gap-3 rounded-full bg-[#f3c969] px-5 py-3.5 text-sm font-medium text-[#2d2a1f] transition hover:translate-y-[-1px] hover:bg-[#f7d47e]">
              <Plus size={18} />
              Start a project
            </button>
          </div>

          <aside className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
            <div className="rounded-[30px] border border-black/6 bg-[#e7d9f1] p-6">
              <ScanText size={28} strokeWidth={1.5} />
              <p className="font-display mt-8 text-3xl">Digital or scanned</p>
              <p className="mt-3 max-w-sm text-sm leading-6 text-[#5e5365]">
                Verbatim maps every page into a shared layout model before translating.
              </p>
            </div>
            <div className="rounded-[30px] border border-black/6 bg-[#f4ca6d] p-6">
              <FileCheck2 size={28} strokeWidth={1.5} />
              <p className="font-display mt-8 text-3xl">Review with context</p>
              <p className="mt-3 max-w-sm text-sm leading-6 text-[#665428]">
                See the rules, terms, memories, and revisions behind every result.
              </p>
            </div>
          </aside>
        </section>

        <section className="rounded-[34px] border border-black/6 bg-white/65 p-5 shadow-[0_20px_70px_rgba(45,42,33,0.06)] lg:p-8">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="text-sm text-[#718078]">Workspace</p>
              <h2 className="font-display mt-1 text-4xl tracking-[-0.025em]">Your projects</h2>
            </div>
            <button className="inline-flex items-center gap-2 rounded-full border border-black/10 bg-white px-4 py-2.5 text-sm shadow-sm">
              <Plus size={16} />
              New project
            </button>
          </div>

          <div className="mt-7 grid gap-4 lg:grid-cols-2">
            {projects.map((project, index) => (
              <article
                key={project.name}
                className="group rounded-[28px] border border-black/7 bg-[#fbfaf6] p-5 transition hover:-translate-y-0.5 hover:shadow-[0_18px_50px_rgba(48,45,36,0.08)]"
              >
                <div className="flex items-start justify-between gap-4">
                  <div
                    className={`grid size-12 place-items-center rounded-2xl ${
                      index === 0 ? "bg-[#d8e7df]" : "bg-[#eee0f7]"
                    }`}
                  >
                    {index === 0 ? <BookOpenText size={22} /> : <FileText size={22} />}
                  </div>
                  <ArrowRight
                    className="text-[#7e8a83] transition group-hover:translate-x-1"
                    size={20}
                  />
                </div>
                <h3 className="font-display mt-8 text-2xl">{project.name}</h3>
                <p className="mt-1 text-sm text-[#718078]">{project.pair}</p>
                <div className="mt-6 grid grid-cols-3 gap-3 border-t border-black/7 pt-5 text-sm">
                  <div>
                    <p className="text-xs text-[#8a938e]">Documents</p>
                    <p className="mt-1 font-medium">{project.documents}</p>
                  </div>
                  <div>
                    <p className="text-xs text-[#8a938e]">Status</p>
                    <p className="mt-1 font-medium">{project.status}</p>
                  </div>
                  <div>
                    <p className="text-xs text-[#8a938e]">Style</p>
                    <p className="mt-1 truncate font-medium">{project.tone}</p>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </section>
      </div>
    </main>
  );
}

export default App;
