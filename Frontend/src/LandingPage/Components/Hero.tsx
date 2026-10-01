
interface HeroProps {
  onSignIn: () => void
  loading?: boolean
}

const Hero = ({ onSignIn, loading = false }: HeroProps) => {
  return (
    <section className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50 flex flex-col items-center justify-center px-6">

      {/* Hero Heading */}
      <div className="text-center mb-12">
        <p className="inline-block px-4 py-2 rounded-full bg-blue-100 text-gray-700 text-sm font-medium mb-4">
          AI Powered Enterprise Knowledge Platform
        </p>

        <h1 className="text-5xl md:text-6xl font-bold text-slate-900 mb-4">
          Turn Knowledge Into
          <span className="text-gray-400"> Intelligence</span>
        </h1>

        <p className="max-w-2xl mx-auto text-slate-600 text-lg">
          Organize, search and discover company knowledge using
          semantic search, embeddings and AI-powered retrieval.
        </p>
      </div>

      {/* BOOK */}
      <div className="relative mb-16">

        {/* Glow */}
        <div className="absolute inset-0 blur-3xl bg-blue-300/30 scale-125 rounded-full"></div>

        {/* Book Container */}
        <div className="relative">

          {/* Book Shadow */}
          <div className="absolute top-5 left-4 w-full h-full bg-slate-300 rounded-xl"></div>

          {/* Spine */}
          <div className="absolute left-0 top-0 h-full w-5 bg-gradient-to-b from-slate-700 to-slate-900 rounded-l-xl z-20"></div>

          {/* Main Book */}
          <div className="relative z-30 w-[320px] h-[420px] bg-gradient-to-br from-gray-600 to-gray-800 rounded-xl shadow-2xl border border-blue-400">

            {/* Cover Content */}
            <div className="h-full flex flex-col justify-center items-center text-white p-8">

              <div className="w-16 h-16 rounded-full bg-white/10 border border-white/20 flex items-center justify-center mb-6">
                📚
              </div>

              <h2 className="text-3xl font-bold mb-2">
                KnowledgeFlow
              </h2>

              <p className="text-center text-blue-100">
                Enterprise Knowledge Platform
              </p>

              <div className="mt-8 w-20 h-[2px] bg-white/30"></div>

              <p className="mt-8 text-center text-sm text-blue-100">
                Click to Begin the Knowledge Journey
              </p>
            </div>
          </div>

          {/* Pages Effect */}
          <div className="absolute top-2 left-2 w-[320px] h-105 bg-white rounded-xl border border-slate-200 -z-10"></div>

          <div className="absolute top-4 left-4 w-[320px] h-105 bg-slate-100 rounded-xl border border-slate-200 -z-20"></div>

        </div>
      </div>

      {/* Buttons */}
      <div className="flex flex-wrap justify-center gap-4">

        <button onClick={onSignIn} disabled={loading} className="px-8 py-4 rounded-xl bg-gray-600 hover:bg-gray-700 text-white font-semibold shadow-lg transition-all duration-300 hover:scale-105 disabled:opacity-60">
          {loading ? 'Connecting...' : 'Sign in with Google →'}
        </button>

        <button onClick={() => document.getElementById('how-it-works')?.scrollIntoView({ behavior: 'smooth' })} className="px-8 py-4 rounded-xl border border-slate-300 bg-white hover:bg-slate-50 text-slate-800 font-semibold shadow-sm transition-all duration-300">
          Explore the platform
        </button>

      </div>

      {/* Hint */}
      <p id="how-it-works" className="mt-8 text-slate-500 text-sm">
        Securely ingest your knowledge and find answers grounded in your documents.
      </p>

    </section>
  );
};

export default Hero;
