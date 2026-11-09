import { useState } from 'react'
import { UserPlus, AlertTriangle } from 'lucide-react'
import { Modal } from '../common/Modal'
import { Button } from '../common/Button'
import { Input } from '../common/Input'
import { Spinner } from '../common/Spinner'
import type { Role } from '../../types/auth'
import type { CreateUserRequest } from '../../types/user'

export interface CreateUserModalProps {
  isOpen: boolean
  onClose: () => void
  onCreate: (request: CreateUserRequest) => Promise<unknown>
}

export function CreateUserModal({ isOpen, onClose, onCreate }: CreateUserModalProps) {
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('OPERATOR')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMessage(null)

    if (!username.trim() || !email.trim() || !password.trim()) {
      setErrorMessage('All fields are required.')
      return
    }

    if (password.length < 8) {
      setErrorMessage('Password must be at least 8 characters.')
      return
    }

    setIsSubmitting(true)
    try {
      await onCreate({
        username: username.trim(),
        email: email.trim(),
        password,
        role,
      })
      // Reset form on success
      setUsername('')
      setEmail('')
      setPassword('')
      setRole('OPERATOR')
      onClose()
    } catch (err: unknown) {
      const msg =
        (err as { message?: string })?.message ||
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to create user. Please check your inputs.'
      setErrorMessage(msg)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={isSubmitting ? () => {} : onClose}
      title="Provision Console User"
      description="Create a new user account and assign RBAC operational permissions."
      size="md"
      footer={
        <div className="flex w-full items-center justify-between">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onClose}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            form="create-user-form"
            variant="primary"
            size="sm"
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <Spinner size="sm" className="mr-1.5" />
                Provisioning...
              </>
            ) : (
              <>
                <UserPlus className="mr-1.5 h-3.5 w-3.5" />
                Create User
              </>
            )}
          </Button>
        </div>
      }
    >
      <form id="create-user-form" onSubmit={handleSubmit} className="space-y-3.5">
        {errorMessage && (
          <div className="flex items-start gap-2.5 rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300">
            <AlertTriangle className="h-4 w-4 shrink-0 text-rose-600 dark:text-rose-400 mt-0.5" />
            <p>{errorMessage}</p>
          </div>
        )}

        <Input
          label="Username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="e.g. operator_john"
          required
          disabled={isSubmitting}
          helperText="Alphanumeric, dots, hyphens, and underscores (3-50 chars)"
        />

        <Input
          label="Email Address"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="e.g. operator@company.com"
          required
          disabled={isSubmitting}
        />

        <Input
          label="Initial Password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="••••••••••••"
          required
          disabled={isSubmitting}
          helperText="Minimum 8 characters. Hashed via BCrypt upon arrival."
        />

        <div className="space-y-1.5">
          <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
            Assigned Role
          </label>
          <div className="grid grid-cols-3 gap-2">
            {(['ADMIN', 'OPERATOR', 'VIEWER'] as Role[]).map((r) => (
              <button
                key={r}
                type="button"
                onClick={() => setRole(r)}
                className={`rounded-md border p-2 text-xs font-medium text-center transition-colors ${
                  role === r
                    ? 'border-indigo-600 bg-indigo-50 text-indigo-700 dark:border-indigo-500 dark:bg-indigo-950/50 dark:text-indigo-300'
                    : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800'
                }`}
              >
                {r}
              </button>
            ))}
          </div>
        </div>
      </form>
    </Modal>
  )
}
