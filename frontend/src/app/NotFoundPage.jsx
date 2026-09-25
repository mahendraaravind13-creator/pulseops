import { Link } from 'react-router-dom'
import { Compass } from 'lucide-react'
import { EmptyState } from '../components/EmptyState'

export function NotFoundPage() {
  return (
    <div className="card">
      <EmptyState
        icon={Compass}
        title="Page not found"
        description="The page you are looking for does not exist."
        action={
          <Link to="/" className="text-sm font-medium text-emerald-400 hover:text-emerald-300">
            Back to overview
          </Link>
        }
      />
    </div>
  )
}
