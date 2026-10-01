import logo from "../assets/image.png";

interface NavBarProps {
  onSignIn: () => void
  loading?: boolean
}

const NavBar = ({ onSignIn, loading = false }: NavBarProps) => {
   return (
    <nav className="sticky top-0 z-50 bg-white shadow-md border-b border-gray-100">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">

          {/* Left Side */}
          <div className="flex items-center gap-3">

            <img
              src={logo}
              alt="Edify Logo"
              className="h-10 w-10 object-contain"
            />

            <span className="text-gray-300 text-xl">|</span>

            <a
              href="/"
              className="text-2xl font-bold tracking-wide text-gray-900"
            >
              EDIFY
            </a>

          </div>

          {/* Right Side */}
          <div className='flex items-center gap-4'>
            <button onClick={onSignIn} disabled={loading} className='px-5 py-2 text-sm font-medium text-gray-700 hover:text-black transition border border-black rounded-md disabled:opacity-60'>
              {loading ? 'Loading...' : 'Sign in'}
            </button>

            <button onClick={onSignIn} disabled={loading} className='px-5 py-2 bg-black text-white rounded-lg hover:bg-gray-800 transition disabled:opacity-60'>
              Get started
            </button>

          </div>
        </div>
      </div>
    </nav>
  );
}

export default NavBar