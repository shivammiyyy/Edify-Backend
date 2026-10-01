import NavBar from '../Commons/NavBar'
import Hero from './Components/Hero'

interface LandingPageProps {
  onSignIn: () => void
  loading?: boolean
}

export const Index = ({ onSignIn, loading = false }: LandingPageProps) => {
  return (
    <div className=''>
        <div >
            <NavBar onSignIn={onSignIn} loading={loading}/>
        </div>
        <Hero onSignIn={onSignIn} loading={loading}/>
    </div>
  )
}
