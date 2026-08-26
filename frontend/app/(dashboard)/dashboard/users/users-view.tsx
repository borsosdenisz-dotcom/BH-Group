"use client"

import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"
import {
  Copy,
  KeyRound,
  Mail,
  MoreVertical,
  Pencil,
  Plus,
  Search,
  ShieldCheck,
  ShieldOff,
  Trash2,
  UserPlus,
  Users as UsersIcon,
  UserX,
} from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Button } from "@/components/ui/button"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Badge } from "@/components/ui/badge"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Skeleton } from "@/components/ui/skeleton"
import { DataPagination } from "@/components/ui/data-pagination"
import {
  useCreateUser,
  useDeleteUser,
  useResendInvite,
  useResetUserMfa,
  useUpdateUser,
  useUpdateUserStatus,
  useUsers,
} from "@/hooks/use-users"
import { useCurrentUser } from "@/hooks/use-current-user"
import {
  ALL_ROLES,
  ALL_USER_STATUSES,
  ROLE_LABELS,
  USER_STATUS_BADGE_VARIANT,
  USER_STATUS_LABELS,
  canAssignRole,
} from "@/lib/roles"
import type { Role, UserResponse, UserStatus } from "@/lib/api/types"

const createUserSchema = z.object({
  firstName: z.string().min(1, "Prenumele este obligatoriu").max(100),
  lastName: z.string().min(1, "Numele este obligatoriu").max(100),
  email: z.string().min(1, "Emailul este obligatoriu").email("Adresă de email invalidă"),
  phone: z.string().max(30).optional(),
  role: z.enum(ALL_ROLES as [Role, ...Role[]]),
})

type CreateUserValues = z.infer<typeof createUserSchema>

const editUserSchema = z.object({
  firstName: z.string().min(1, "Prenumele este obligatoriu").max(100),
  lastName: z.string().min(1, "Numele este obligatoriu").max(100),
  phone: z.string().max(30).optional(),
  role: z.enum(ALL_ROLES as [Role, ...Role[]]),
})

type EditUserValues = z.infer<typeof editUserSchema>

function CreateUserDialog({ actingRole }: { actingRole?: Role }) {
  const [open, setOpen] = useState(false)
  const [createdInvite, setCreatedInvite] = useState<{ name: string; url: string } | null>(null)
  const createUser = useCreateUser()
  const assignableRoles = ALL_ROLES.filter((r) => canAssignRole(actingRole, r))
  const defaultRole = assignableRoles[0]

  const form = useForm<CreateUserValues>({
    resolver: zodResolver(createUserSchema),
    defaultValues: {
      firstName: "",
      lastName: "",
      email: "",
      phone: "",
      role: defaultRole,
    },
  })

  function resetForm() {
    form.reset({ firstName: "", lastName: "", email: "", phone: "", role: defaultRole })
  }

  function handleOpenChange(next: boolean) {
    setOpen(next)
    if (!next) {
      setCreatedInvite(null)
      resetForm()
    }
  }

  function onSubmit(values: CreateUserValues) {
    createUser.mutate(
      {
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        phone: values.phone || undefined,
        role: values.role,
      },
      {
        onSuccess: (data) => {
          resetForm()
          if (data.inviteUrl) {
            setCreatedInvite({ name: `${data.firstName} ${data.lastName}`, url: data.inviteUrl })
          } else {
            setOpen(false)
          }
        },
      }
    )
  }

  function handleCopy() {
    if (!createdInvite) return
    navigator.clipboard.writeText(createdInvite.url)
    toast.success("Link copiat")
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={<Button />}>
        <Plus className="size-4" />
        Adaugă membru
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        {createdInvite ? (
          <>
            <DialogHeader>
              <DialogTitle>Cont creat pentru {createdInvite.name}</DialogTitle>
              <DialogDescription>
                Emailul de invitație a fost trimis automat. Dacă serverul de email nu e încă
                configurat, trimite acest link persoanei ca să-și seteze parola:
              </DialogDescription>
            </DialogHeader>
            <div className="flex gap-2">
              <Input readOnly value={createdInvite.url} className="font-mono text-xs" />
              <Button type="button" variant="outline" size="icon" onClick={handleCopy} aria-label="Copiază link">
                <Copy className="size-4" />
              </Button>
            </div>
            <Button type="button" className="w-full" onClick={() => handleOpenChange(false)}>
              Am notat, închide
            </Button>
          </>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>Cont nou de echipă</DialogTitle>
              <DialogDescription>
                Colegul primește un email de invitație și își alege singur parola pentru a-și activa
                contul — tu nu vezi și nu transmiți nicio parolă. Dacă emailul nu ajunge, poți copia
                linkul de invitație și i-l trimiți manual. 2FA îl activează tot el, din contul lui,
                din Setări.
              </DialogDescription>
            </DialogHeader>
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4">
                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="firstName"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Prenume</FormLabel>
                        <FormControl>
                          <Input placeholder="Ion" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="lastName"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Nume</FormLabel>
                        <FormControl>
                          <Input placeholder="Popescu" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <FormField
                  control={form.control}
                  name="email"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Email</FormLabel>
                      <FormControl>
                        <Input type="email" placeholder="nume@bhgroup.io" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="phone"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Telefon (opțional)</FormLabel>
                        <FormControl>
                          <Input placeholder="07xx xxx xxx" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="role"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Rol</FormLabel>
                        <Select value={field.value} onValueChange={field.onChange}>
                          <FormControl>
                            <SelectTrigger className="w-full">
                              <SelectValue />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {assignableRoles.map((r) => (
                              <SelectItem key={r} value={r}>
                                {ROLE_LABELS[r]}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <Button type="submit" className="w-full" disabled={createUser.isPending}>
                  <UserPlus className="size-4" />
                  {createUser.isPending ? "Se trimite..." : "Trimite invitația"}
                </Button>
              </form>
            </Form>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

function EditUserDialog({
  user,
  actingRole,
  open,
  onOpenChange,
}: {
  user: UserResponse
  actingRole?: Role
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const updateUser = useUpdateUser()
  const assignableRoles = ALL_ROLES.filter((r) => canAssignRole(actingRole, r))

  const form = useForm<EditUserValues>({
    resolver: zodResolver(editUserSchema),
    defaultValues: {
      firstName: user.firstName,
      lastName: user.lastName,
      phone: user.phone ?? "",
      role: user.role,
    },
  })

  function handleOpenChange(next: boolean) {
    onOpenChange(next)
    if (next) {
      form.reset({
        firstName: user.firstName,
        lastName: user.lastName,
        phone: user.phone ?? "",
        role: user.role,
      })
    }
  }

  function onSubmit(values: EditUserValues) {
    updateUser.mutate(
      {
        id: user.id,
        payload: {
          firstName: values.firstName,
          lastName: values.lastName,
          phone: values.phone || undefined,
          role: values.role,
        },
      },
      { onSuccess: () => onOpenChange(false) }
    )
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Editează cont</DialogTitle>
          <DialogDescription>{user.email}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="firstName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Prenume</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="lastName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Nume</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <FormField
              control={form.control}
              name="phone"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Telefon (opțional)</FormLabel>
                  <FormControl>
                    <Input placeholder="07xx xxx xxx" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="role"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Rol</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {assignableRoles.map((r) => (
                        <SelectItem key={r} value={r}>
                          {ROLE_LABELS[r]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button type="submit" className="w-full" disabled={updateUser.isPending}>
              {updateUser.isPending ? "Se salvează..." : "Salvează"}
            </Button>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}

function DisableUserDialog({
  user,
  open,
  onOpenChange,
}: {
  user: UserResponse
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [typedEmail, setTypedEmail] = useState("")
  const updateStatus = useUpdateUserStatus()

  function handleConfirm() {
    updateStatus.mutate(
      { id: user.id, status: "DISABLED", confirmEmail: typedEmail },
      { onSuccess: () => { onOpenChange(false); setTypedEmail("") } }
    )
  }

  return (
    <AlertDialog
      open={open}
      onOpenChange={(next) => { onOpenChange(next); if (!next) setTypedEmail("") }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Dezactivezi definitiv acest cont?</AlertDialogTitle>
          <AlertDialogDescription>
            {user.firstName} {user.lastName} nu se va mai putea autentifica. Acțiunea este
            greu de anulat. Scrie adresa de email a contului pentru a confirma:{" "}
            <strong>{user.email}</strong>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <Input
          value={typedEmail}
          onChange={(e) => setTypedEmail(e.target.value)}
          placeholder={user.email}
          autoComplete="off"
        />
        <AlertDialogFooter>
          <AlertDialogCancel>Anulează</AlertDialogCancel>
          <AlertDialogAction
            disabled={typedEmail.trim().toLowerCase() !== user.email.toLowerCase() || updateStatus.isPending}
            onClick={handleConfirm}
          >
            Dezactivează definitiv
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

function DeleteUserDialog({
  user,
  open,
  onOpenChange,
}: {
  user: UserResponse
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const deleteUser = useDeleteUser()

  function handleConfirm() {
    deleteUser.mutate(user.id, { onSuccess: () => onOpenChange(false) })
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Ștergi definitiv acest cont?</AlertDialogTitle>
          <AlertDialogDescription>
            {user.firstName} {user.lastName} ({user.email}) va fi șters din platformă, împreună
            cu sesiunile și invitațiile asociate. Ești sigur? Această acțiune este permanentă.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Anulează</AlertDialogCancel>
          <AlertDialogAction disabled={deleteUser.isPending} onClick={handleConfirm}>
            Șterge
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

function UserRowActions({
  user,
  meId,
  actingRole,
  isSuperAdmin,
}: {
  user: UserResponse
  meId?: string
  actingRole?: Role
  isSuperAdmin: boolean
}) {
  const [editOpen, setEditOpen] = useState(false)
  const [disableOpen, setDisableOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const updateStatus = useUpdateUserStatus()
  const resetMfa = useResetUserMfa()
  const resendInvite = useResendInvite()

  const canTouch = canAssignRole(actingRole, user.role)
  const isSelf = user.id === meId

  function toggleStatus() {
    const next: UserStatus = user.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE"
    updateStatus.mutate({ id: user.id, status: next })
  }

  function copyInviteLink() {
    resendInvite.mutate(user.id, {
      onSuccess: (data) => {
        if (data.inviteUrl) {
          navigator.clipboard.writeText(data.inviteUrl)
          toast.success("Link copiat în clipboard")
        }
      },
    })
  }

  if (!canTouch) {
    return <span className="text-sm text-muted-foreground">—</span>
  }

  const hasAnyAction =
    !isSelf ||
    user.status === "PENDING" ||
    (isSuperAdmin && user.mfaEnabled)

  if (!hasAnyAction) {
    return <span className="text-sm text-muted-foreground">—</span>
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon" aria-label="Acțiuni cont" />}>
          <MoreVertical className="size-4" />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {!isSelf && (
            <DropdownMenuItem onClick={() => setEditOpen(true)}>
              <Pencil className="size-4" />
              Editează
            </DropdownMenuItem>
          )}
          {user.status === "PENDING" && (
            <DropdownMenuItem disabled={resendInvite.isPending} onClick={() => resendInvite.mutate(user.id)}>
              <Mail className="size-4" />
              Retrimite invitația
            </DropdownMenuItem>
          )}
          {user.status === "PENDING" && (
            <DropdownMenuItem disabled={resendInvite.isPending} onClick={copyInviteLink}>
              <Copy className="size-4" />
              Copiază link invitație
            </DropdownMenuItem>
          )}
          {isSuperAdmin && user.mfaEnabled && (
            <DropdownMenuItem disabled={resetMfa.isPending} onClick={() => resetMfa.mutate(user.id)}>
              <ShieldOff className="size-4" />
              Resetează 2FA
            </DropdownMenuItem>
          )}
          {!isSelf && (user.status === "ACTIVE" || user.status === "SUSPENDED") && (
            <DropdownMenuItem disabled={updateStatus.isPending} onClick={toggleStatus}>
              {user.status === "ACTIVE" ? "Suspendă" : "Reactivează"}
            </DropdownMenuItem>
          )}
          {!isSelf && user.status !== "DISABLED" && (
            <DropdownMenuItem variant="destructive" onClick={() => setDisableOpen(true)}>
              <Trash2 className="size-4" />
              Dezactivează definitiv
            </DropdownMenuItem>
          )}
          {!isSelf && isSuperAdmin && (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" onClick={() => setDeleteOpen(true)}>
                <UserX className="size-4" />
                Șterge cont
              </DropdownMenuItem>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
      <EditUserDialog user={user} actingRole={actingRole} open={editOpen} onOpenChange={setEditOpen} />
      <DisableUserDialog user={user} open={disableOpen} onOpenChange={setDisableOpen} />
      <DeleteUserDialog user={user} open={deleteOpen} onOpenChange={setDeleteOpen} />
    </>
  )
}

function UsersTableSkeleton() {
  return (
    <div className="rounded-lg border border-border/60 p-3">
      <div className="flex flex-col gap-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="flex items-center gap-4">
            <Skeleton className="h-4 flex-[2]" />
            <Skeleton className="h-4 flex-[2]" />
            <Skeleton className="h-5 w-20 rounded-full" />
            <Skeleton className="h-4 w-12" />
            <Skeleton className="h-5 w-20 rounded-full" />
            <Skeleton className="size-8 shrink-0 rounded-md" />
          </div>
        ))}
      </div>
    </div>
  )
}

export function UsersView() {
  const { data: me } = useCurrentUser()
  const [search, setSearch] = useState("")
  const [roleFilter, setRoleFilter] = useState<Role | "ALL">("ALL")
  const [statusFilter, setStatusFilter] = useState<UserStatus | "ALL">("ALL")
  const [page, setPage] = useState(0)

  const { data, isLoading } = useUsers({
    search: search || undefined,
    role: roleFilter === "ALL" ? undefined : roleFilter,
    status: statusFilter === "ALL" ? undefined : statusFilter,
    page,
  })

  const isSuperAdmin = me?.role === "SUPER_ADMIN"
  const isAdmin = isSuperAdmin || me?.role === "ADMINISTRATOR"
  const hasFilters = search.trim() !== "" || roleFilter !== "ALL" || statusFilter !== "ALL"

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Echipă</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Administrează conturile colegilor care au acces în platformă.
          </p>
        </div>
        {isAdmin && <CreateUserDialog actingRole={me?.role} />}
      </div>

      {!isAdmin && (
        <div className="flex items-center gap-2 rounded-lg bg-amber-500/10 px-3 py-2 text-sm text-amber-600 dark:text-amber-400">
          <KeyRound className="size-4" />
          Doar administratorii pot crea sau modifica alte conturi.
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-64 flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Caută după nume sau email..."
            className="pl-9"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          />
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">Rol:</span>
          <Select
            value={roleFilter}
            onValueChange={(v) => { setRoleFilter(v as Role | "ALL"); setPage(0) }}
          >
            <SelectTrigger className="w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Toate</SelectItem>
              {ALL_ROLES.map((r) => (
                <SelectItem key={r} value={r}>
                  {ROLE_LABELS[r]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">Status:</span>
          <Select
            value={statusFilter}
            onValueChange={(v) => { setStatusFilter(v as UserStatus | "ALL"); setPage(0) }}
          >
            <SelectTrigger className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Toate</SelectItem>
              {ALL_USER_STATUSES.map((s) => (
                <SelectItem key={s} value={s}>
                  {USER_STATUS_LABELS[s]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {isLoading ? (
        <UsersTableSkeleton />
      ) : !data || data.content.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-16 text-center">
          <UsersIcon className="size-8 text-muted-foreground/50" />
          <p className="text-sm text-muted-foreground">
            {hasFilters
              ? "Niciun cont nu corespunde filtrelor curente."
              : "Nu au fost găsiți utilizatori."}
          </p>
        </div>
      ) : (
        <>
          <div className="rounded-lg border border-border/60">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nume</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Rol</TableHead>
                  <TableHead>2FA</TableHead>
                  <TableHead>Status</TableHead>
                  {isAdmin && <TableHead className="text-right">Acțiuni</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((u) => (
                  <TableRow key={u.id}>
                    <TableCell className="font-medium">
                      {u.firstName} {u.lastName}
                      {u.id === me?.id && (
                        <span className="ml-2 text-xs text-muted-foreground">(tu)</span>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{u.email}</TableCell>
                    <TableCell>
                      <Badge variant="secondary">{ROLE_LABELS[u.role]}</Badge>
                    </TableCell>
                    <TableCell>
                      {u.mfaEnabled ? (
                        <span className="inline-flex items-center gap-1 text-xs text-emerald-600 dark:text-emerald-400">
                          <ShieldCheck className="size-3.5" /> Activ
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                          <ShieldOff className="size-3.5" /> Inactiv
                        </span>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant={USER_STATUS_BADGE_VARIANT[u.status]}>
                        {USER_STATUS_LABELS[u.status]}
                      </Badge>
                    </TableCell>
                    {isAdmin && (
                      <TableCell className="text-right">
                        <UserRowActions
                          user={u}
                          meId={me?.id}
                          actingRole={me?.role}
                          isSuperAdmin={isSuperAdmin}
                        />
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <DataPagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  )
}
