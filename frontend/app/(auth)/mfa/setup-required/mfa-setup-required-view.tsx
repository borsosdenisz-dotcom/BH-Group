"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { ShieldAlert } from "lucide-react"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { MfaSetupFlow } from "@/components/auth/mfa-setup-flow"
import { useAuthStore } from "@/lib/stores/auth-store"
import { authApi } from "@/lib/api/auth"

type SessionCheck = "checking" | "present" | "absent"

export function MfaSetupRequiredView() {
  const router = useRouter()
  // This page can be reached two ways: a normal in-app navigation right
  // after login (accessToken already in memory - the common case now), or a
  // hard-reload redirect from api/client.ts when some other page hits the
  // backend's mandatory-MFA gate (403 MFA_SETUP_REQUIRED). accessToken is
  // deliberately NOT persisted across reloads (see auth-store.ts), so a hard
  // reload always starts "checking" here rather than trusting a bare
  // in-memory read - the httpOnly refresh-token cookie is still valid in
  // that case, so try it before concluding the user isn't logged in. A bare
  // accessToken check would false-negative on every hard-reload arrival and
  // bounce a genuinely authenticated user to /login - which, since the
  // session cookie is still set, the auth middleware immediately bounces
  // back to /dashboard, which redirects here again: an infinite loop.
  const [sessionCheck, setSessionCheck] = useState<SessionCheck>(() =>
    useAuthStore.getState().accessToken ? "present" : "checking"
  )

  useEffect(() => {
    if (sessionCheck !== "checking") return
    authApi
      .refresh()
      .then((result) => {
        useAuthStore.getState().setSession(result)
        setSessionCheck("present")
      })
      .catch(() => setSessionCheck("absent"))
  }, [sessionCheck])

  useEffect(() => {
    if (sessionCheck === "absent") {
      router.replace("/login")
    }
  }, [sessionCheck, router])

  function handleComplete() {
    router.push("/dashboard")
  }

  if (sessionCheck !== "present") {
    return null
  }

  return (
    <Card className="border-white/15 bg-background/95 shadow-2xl backdrop-blur-md">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-xl">
          <ShieldAlert className="size-5 text-amber-500" />
          Configurare obligatorie 2FA
        </CardTitle>
        <CardDescription>
          Contul tău necesită autentificare în doi pași. Nu poți continua în aplicație până nu o
          configurezi.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <MfaSetupFlow onComplete={handleComplete} completeLabel="Continuă către aplicație" />
      </CardContent>
    </Card>
  )
}
