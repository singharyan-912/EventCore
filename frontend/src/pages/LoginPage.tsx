import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Server, Lock, User, AlertCircle } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { Button } from '../components/common/Button'
import { authService } from '../services/authService'

export function LoginPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username.trim() || !password) {
      setErrorMessage('Please enter both username and password.')
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      await authService.login({ username: username.trim(), password })
      navigate('/')
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      setErrorMessage(
        error?.response?.data?.message || 'Invalid username or password. Please try again.'
      )
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950 p-4 transition-colors">
      <div className="w-full max-w-md space-y-6">
        {/* Brand Banner */}
        <div className="text-center space-y-2">
          <div className="inline-flex h-12 w-12 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-md dark:bg-indigo-500">
            <Server className="h-6 w-6" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
            ReliableFlow Console
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Sign in to access event processing, outbox metrics, and recovery operations.
          </p>
        </div>

        {/* Login Card */}
        <Card className="border-slate-200 shadow-sm dark:border-slate-800">
          <CardHeader className="border-b-0 pb-0">
            <CardTitle className="text-base">Sign In</CardTitle>
            <CardDescription>
              Backend authenticated via <code>POST /api/v1/auth/login</code>
            </CardDescription>
          </CardHeader>
          <CardContent className="pt-4">
            <form onSubmit={handleSubmit} className="space-y-4">
              {errorMessage && (
                <div className="flex items-center gap-2 rounded-md bg-rose-50 border border-rose-200 p-3 text-xs text-rose-700 dark:bg-rose-950/60 dark:border-rose-900 dark:text-rose-300">
                  <AlertCircle className="h-4 w-4 shrink-0" />
                  <span>{errorMessage}</span>
                </div>
              )}

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">Username</label>
                <div className="relative">
                  <User className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                  <input
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="Enter your username"
                    className="w-full rounded-md border border-slate-300 bg-white py-2 pl-9 pr-3 text-xs text-slate-900 placeholder:text-slate-400 focus:border-indigo-600 focus:outline-none focus:ring-1 focus:ring-indigo-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder:text-slate-500"
                    disabled={isLoading}
                    autoComplete="username"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">Password</label>
                <div className="relative">
                  <Lock className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                  <input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter your password"
                    className="w-full rounded-md border border-slate-300 bg-white py-2 pl-9 pr-3 text-xs text-slate-900 placeholder:text-slate-400 focus:border-indigo-600 focus:outline-none focus:ring-1 focus:ring-indigo-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder:text-slate-500"
                    disabled={isLoading}
                    autoComplete="current-password"
                  />
                </div>
              </div>

              <Button
                type="submit"
                variant="primary"
                size="md"
                className="w-full"
                isLoading={isLoading}
              >
                Sign In to Console
              </Button>
            </form>

            <div className="mt-4 rounded-md bg-slate-50 border border-slate-200 p-2.5 text-[11px] text-slate-500 space-y-1 dark:bg-slate-800/50 dark:border-slate-800 dark:text-slate-400">
              <span className="font-semibold text-slate-700 dark:text-slate-300 block">Development Credentials:</span>
              <p>Username: <code className="font-mono text-slate-800 dark:text-slate-200">admin</code></p>
              <p>Password: <code className="font-mono text-slate-800 dark:text-slate-200">AdminSecret123!</code></p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

