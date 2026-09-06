"use client"

import { useState } from "react"
import { QRCodeSVG } from "qrcode.react"
import { Check, Copy, Download, ShieldCheck } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { useEnableMfa, useSetupMfa } from "@/hooks/use-auth"

interface MfaSetupFlowProps {
  /** Called after the user has acknowledged their recovery codes. */
  onComplete: () => void
  completeLabel: string
  /**
   * The account already has MFA enabled (reconfiguring/replacing a lost
   * device), so the backend requires the current password before it will
   * regenerate the secret - proof beyond just holding a valid access token.
   */
  requirePasswordConfirmation?: boolean
}

export function MfaSetupFlow({ onComplete, completeLabel, requirePasswordConfirmation }: MfaSetupFlowProps) {
  const setupMfa = useSetupMfa()
  const enableMfa = useEnableMfa()
  const [enableCode, setEnableCode] = useState("")
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null)
  const [copied, setCopied] = useState(false)
  const [acknowledged, setAcknowledged] = useState(false)
  const [confirmingPassword, setConfirmingPassword] = useState(false)
  const [password, setPassword] = useState("")

  function handleConfirmEnable() {
    enableMfa.mutate(enableCode, {
      onSuccess: (response) => {
        setRecoveryCodes(response.recoveryCodes)
        setEnableCode("")
      },
    })
  }

  function handleCopyCodes() {
    if (!recoveryCodes) return
    navigator.clipboard.writeText(recoveryCodes.join("\n")).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  function handleDownloadCodes() {
    if (!recoveryCodes) return
    const blob = new Blob([`Coduri de recuperare BH Group\n\n${recoveryCodes.join("\n")}\n`], { type: "text/plain;charset=utf-8" })
    const url = URL.createObjectURL(blob)
    const link = document.createElement("a")
    link.href = url
    link.download = "bh-group-coduri-recuperare.txt"
    link.click()
    URL.revokeObjectURL(url)
  }

  if (recoveryCodes) {
    return (
      <div className="flex flex-col gap-4 border border-success/25 bg-success/5 p-5 sm:p-6">
        <div className="flex items-center gap-2 text-sm font-semibold text-success">
          <ShieldCheck className="size-4" />
          2FA activat cu succes
        </div>
        <p className="text-sm text-muted-foreground">
          Salvează aceste coduri de recuperare într-un loc sigur. Fiecare poate fi folosit o
          singură dată pentru a te autentifica dacă pierzi accesul la aplicația de autentificare.
          Nu vor mai fi afișate din nou.
        </p>
        <div className="grid grid-cols-1 gap-2 border border-border bg-card p-4 font-mono text-sm sm:grid-cols-2">
          {recoveryCodes.map((code) => (
            <span key={code} className="break-all">{code}</span>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" size="sm" className="gap-2" onClick={handleCopyCodes}>{copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />}{copied ? "Copiat" : "Copiază codurile"}</Button>
          <Button type="button" variant="outline" size="sm" className="gap-2" onClick={handleDownloadCodes}><Download className="size-3.5" />Salvează fișierul</Button>
        </div>
        <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
          <Checkbox checked={acknowledged} onCheckedChange={(checked) => setAcknowledged(checked === true)} />
          Am salvat aceste coduri într-un loc sigur
        </label>
        <Button type="button" disabled={!acknowledged} onClick={onComplete}>
          {completeLabel}
        </Button>
      </div>
    )
  }

  if (!setupMfa.data) {
    if (requirePasswordConfirmation && !confirmingPassword) {
      return (
        <Button onClick={() => setConfirmingPassword(true)} className="w-fit">
          Reconfigurează 2FA
        </Button>
      )
    }

    if (requirePasswordConfirmation) {
      return (
        <div className="flex flex-col gap-3 border border-border p-4">
          <Label htmlFor="setup-password">Confirmă parola pentru a reconfigura 2FA</Label>
          <Input
            id="setup-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
          <div className="flex gap-2">
            <Button
              size="sm"
              disabled={!password || setupMfa.isPending}
              onClick={() => setupMfa.mutate(password)}
            >
              {setupMfa.isPending ? "Se generează..." : "Continuă"}
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => {
                setConfirmingPassword(false)
                setPassword("")
              }}
            >
              Anulează
            </Button>
          </div>
        </div>
      )
    }

    return (
      <Button onClick={() => setupMfa.mutate(undefined)} disabled={setupMfa.isPending} className="w-fit">
        {setupMfa.isPending ? "Se generează..." : "Configurează 2FA"}
      </Button>
    )
  }

  return (
    <div className="flex flex-col items-center gap-5 border border-border p-5 sm:p-6">
      <ol className="w-full space-y-2 text-sm leading-6 text-muted-foreground">
        <li><strong className="text-foreground">1.</strong> Deschide Google Authenticator, Authy sau o aplicație TOTP compatibilă.</li>
        <li><strong className="text-foreground">2.</strong> Alege adăugarea unui cont și scanează codul QR.</li>
        <li><strong className="text-foreground">3.</strong> Introdu mai jos codul de 6 cifre generat de aplicație.</li>
      </ol>
      <div className="rounded-md border border-border bg-white p-4">
        <QRCodeSVG value={setupMfa.data.otpAuthUrl} size={180} />
      </div>
      <p className="break-all text-center text-xs text-muted-foreground">
        Sau introdu manual cheia: <code className="font-mono">{setupMfa.data.secret}</code>
      </p>
      <div className="flex w-full max-w-52 flex-col gap-2">
        <Label htmlFor="enable-code">Cod de 6 cifre</Label>
        <Input
          id="enable-code"
          inputMode="numeric"
          maxLength={6}
          placeholder="123456"
          value={enableCode}
          onChange={(e) => setEnableCode(e.target.value.replace(/\D/g, ""))}
          className="text-center text-lg tracking-widest"
        />
      </div>
      <Button
        disabled={enableCode.length !== 6 || enableMfa.isPending}
        onClick={handleConfirmEnable}
      >
        {enableMfa.isPending ? "Se confirmă..." : "Confirmă și activează"}
      </Button>
    </div>
  )
}
