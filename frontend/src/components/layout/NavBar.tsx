import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'

const navItems = [
  { to: '/', label: 'Dashboard' },
  { to: '/expenses', label: 'Expenses' },
  { to: '/merchants', label: 'Merchants' },
  { to: '/settings', label: 'Settings' },
]

export default function NavBar() {
  return (
    <nav className="flex items-center gap-6 px-6 py-4 bg-card border-b flex-wrap">
      <a href="/" className="text-xl font-bold text-white no-underline">
        <span className="text-primary">$</span> Expense Tracker
      </a>
      <div className="flex gap-1">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              cn(
                'px-4 py-2 rounded-md text-sm font-medium transition-colors no-underline',
                isActive
                  ? 'bg-primary text-primary-foreground'
                  : 'text-muted-foreground hover:bg-secondary hover:text-foreground'
              )
            }
          >
            {item.label}
          </NavLink>
        ))}
      </div>
      <div className="ml-auto">
        <form action="/logout" method="POST">
          <button
            type="submit"
            className="px-4 py-2 rounded-md text-sm border border-border bg-transparent text-muted-foreground hover:bg-destructive hover:text-white hover:border-destructive transition-colors cursor-pointer"
          >
            Logout
          </button>
        </form>
      </div>
    </nav>
  )
}
