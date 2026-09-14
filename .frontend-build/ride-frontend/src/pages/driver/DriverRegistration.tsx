import { ArrowLeft, CheckCircle, Clock, SealWarning } from '@phosphor-icons/react'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import LanguageSwitcher from '../../components/LanguageSwitcher'
import { useEnsurePassengerSession } from '../../application/session/useEnsurePassengerSession'
import { resolveDriverFormText } from '../../lib/driverFormText'
import { enterDriverCabinet } from '../../lib/driverPortal'
import {
  getDriverRegistrationForm,
  getMyDriverApplication,
  submitDriverApplication,
  updateCurrentUserLanguage,
  uploadDriverApplicationFile,
} from '../../lib/backend'
import { hapticNotification, hapticSelection } from '../../lib/telegram'
import type { AppLanguage } from '../../i18n/languages'
import { normalizeLanguage } from '../../i18n/languages'
import type { DriverApplication, DriverApplicationFileEntry, DriverRegistrationFormSchema } from '../../types'
import { DynamicFormField } from './components/DynamicFormField'

const DRIVER_REGISTRATION_DRAFT_KEY = 'ride_driver_registration_draft_v1'

type DriverRegistrationDraft = {
  answers: Record<string, string>
  files: Record<string, DriverApplicationFileEntry>
  previews: Record<string, DriverRegistrationDraftPreview>
}

type DriverRegistrationDraftPreview = {
  previewUrl: string
  previewDataUrl?: string
  fileName: string
  contentType: string
  sizeBytes: number
}

function readDriverRegistrationDraft(): DriverRegistrationDraft {
  if (typeof window === 'undefined') {
    return { answers: {}, files: {}, previews: {} }
  }
  try {
    const raw = window.localStorage.getItem(DRIVER_REGISTRATION_DRAFT_KEY)
    if (!raw) return { answers: {}, files: {}, previews: {} }
    const parsed = JSON.parse(raw) as Partial<DriverRegistrationDraft>
    const previews = parsed.previews && typeof parsed.previews === 'object' ? parsed.previews : {}
    return {
      answers: parsed.answers && typeof parsed.answers === 'object' ? parsed.answers : {},
      files: parsed.files && typeof parsed.files === 'object' ? parsed.files : {},
      previews: Object.fromEntries(
        Object.entries(previews).map(([fieldId, preview]) => [
          fieldId,
          {
            previewUrl:
              typeof preview?.previewDataUrl === 'string' && preview.previewDataUrl
                ? preview.previewDataUrl
                : typeof preview?.previewUrl === 'string'
                  ? preview.previewUrl
                  : '',
            fileName: typeof preview?.fileName === 'string' ? preview.fileName : '',
            contentType: typeof preview?.contentType === 'string' ? preview.contentType : 'application/octet-stream',
            sizeBytes: typeof preview?.sizeBytes === 'number' ? preview.sizeBytes : 0,
          },
        ]),
      ),
    }
  } catch {
    return { answers: {}, files: {}, previews: {} }
  }
}

function writeDriverRegistrationDraft(draft: DriverRegistrationDraft): void {
  if (typeof window === 'undefined') return
  try {
    const normalizedDraft: DriverRegistrationDraft = {
      answers: draft.answers,
      files: draft.files,
      previews: Object.fromEntries(
        Object.entries(draft.previews).map(([fieldId, preview]) => [
          fieldId,
          {
            previewUrl:
              typeof preview.previewDataUrl === 'string' && preview.previewDataUrl
                ? preview.previewDataUrl
                : preview.previewUrl.startsWith('blob:')
                  ? ''
                  : preview.previewUrl,
            fileName: preview.fileName,
            contentType: preview.contentType,
            sizeBytes: preview.sizeBytes,
          },
        ]),
      ),
    }
    window.localStorage.setItem(DRIVER_REGISTRATION_DRAFT_KEY, JSON.stringify(normalizedDraft))
  } catch {
    // Ignore persistence failures.
  }
}

function clearDriverRegistrationDraft(): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(DRIVER_REGISTRATION_DRAFT_KEY)
  } catch {
    // Ignore persistence failures.
  }
}

function persistDriverRegistrationDraftSnapshot(
  answers: Record<string, string>,
  files: Record<string, DriverApplicationFileEntry>,
  previews: Record<string, DriverRegistrationDraftPreview>,
): void {
  writeDriverRegistrationDraft({ answers, files, previews })
}

export default function DriverRegistration() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const session = useEnsurePassengerSession()
  const [schema, setSchema] = useState<DriverRegistrationFormSchema | null>(null)
  const [application, setApplication] = useState<DriverApplication | null>(null)
  const [answers, setAnswers] = useState<Record<string, string>>({})
  const [files, setFiles] = useState<Record<string, DriverApplicationFileEntry>>({})
  const [selectedFileNames, setSelectedFileNames] = useState<Record<string, string>>({})
  const [filePreviews, setFilePreviews] = useState<Record<string, {
    previewUrl: string
    previewDataUrl?: string
    fileName: string
    contentType: string
    sizeBytes: number
  }>>({})
  const [uploadingFields, setUploadingFields] = useState<Record<string, boolean>>({})
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isEnteringDriverCabinet, setIsEnteringDriverCabinet] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleOpenDriverCabinet = async () => {
    setIsEnteringDriverCabinet(true)
    setErrorMessage(null)
    try {
      await enterDriverCabinet()
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : t('driverRegistration.openCabinetFailed', { defaultValue: 'Failed to open driver cabinet.' }),
      )
    } finally {
      setIsEnteringDriverCabinet(false)
    }
  }

  const sortedFields = useMemo(
    () => (schema?.fields ?? []).slice().sort((a, b) => a.order - b.order || a.id.localeCompare(b.id)),
    [schema],
  )

  useEffect(() => {
    if (!session.isReady) return
    let cancelled = false
    void (async () => {
      setIsLoading(true)
      try {
        const [formSchema, myApplication] = await Promise.all([
          getDriverRegistrationForm(),
          getMyDriverApplication(),
        ])
        if (cancelled) return
        setSchema(formSchema)
        setApplication(myApplication)
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(error instanceof Error ? error.message : t('common.error'))
        }
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [session.isReady, t])

  useEffect(() => {
    if (!session.isReady) return
    const draft = readDriverRegistrationDraft()
    if (Object.keys(draft.answers).length > 0) {
      setAnswers((prev) => (Object.keys(prev).length > 0 ? prev : draft.answers))
    }
    if (Object.keys(draft.files).length > 0) {
      setFiles((prev) => (Object.keys(prev).length > 0 ? prev : draft.files))
    }
    if (Object.keys(draft.previews).length > 0) {
      setFilePreviews((prev) => (Object.keys(prev).length > 0 ? prev : draft.previews))
    }
  }, [session.isReady])

  useEffect(() => {
    if (!session.isReady) return
    writeDriverRegistrationDraft({ answers, files, previews: filePreviews })
  }, [answers, files, filePreviews, session.isReady])

  const introText = schema ? resolveDriverFormText(schema.introText, i18n.language) : ''

  useEffect(() => {
    return () => {
      Object.values(filePreviews).forEach((preview) => {
        if (preview.previewUrl.startsWith('blob:')) {
          URL.revokeObjectURL(preview.previewUrl)
        }
      })
    }
  }, [filePreviews])

  const validateClient = (): boolean => {
    if (!schema) return false
    const nextErrors: Record<string, string> = {}
    for (const field of sortedFields) {
      if (!field.required) continue
      if (field.type === 'file') {
        if (!files[field.id]) nextErrors[field.id] = t('driverRegistration.fieldRequired')
      } else if (!answers[field.id]?.trim()) {
        nextErrors[field.id] = t('driverRegistration.fieldRequired')
      }
    }
    setFieldErrors(nextErrors)
    return Object.keys(nextErrors).length === 0
  }

  const handleSubmit = async () => {
    if (!schema || !validateClient()) return
    hapticSelection()
    setIsSubmitting(true)
    setErrorMessage(null)
    try {
      const language = normalizeLanguage(i18n.language) as AppLanguage
      const submitted = await submitDriverApplication({
        language,
        answers,
        files: Object.fromEntries(
          Object.entries(files).map(([fieldId, entry]) => [
            fieldId,
            {
              objectKey: entry.objectKey,
              fileName: entry.fileName,
              contentType: entry.contentType,
              sizeBytes: entry.sizeBytes,
            },
          ]),
        ),
      })
      setApplication(submitted)
      clearDriverRegistrationDraft()
      hapticNotification('success')
    } catch (error) {
      hapticNotification('error')
      setErrorMessage(error instanceof Error ? error.message : t('common.error'))
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleFileSelect = async (fieldId: string, file: File) => {
    if (file.size > 5 * 1024 * 1024) {
      setFieldErrors((prev) => ({ ...prev, [fieldId]: t('driverRegistration.fileTooLarge') }))
      return
    }
    console.info('[DriverRegistration] upload start', {
      fieldId,
      fileName: file.name,
      fileType: file.type,
      fileSize: file.size,
    })
    setSelectedFileNames((prev) => ({ ...prev, [fieldId]: file.name }))
    const previewUrl = URL.createObjectURL(file)
    let previewDataUrl = previewUrl
    setFilePreviews((prev) => ({
      ...prev,
      [fieldId]: {
        previewUrl,
        fileName: file.name,
        contentType: file.type || 'application/octet-stream',
        sizeBytes: file.size,
      },
    }))
    persistDriverRegistrationDraftSnapshot(answers, files, {
      ...filePreviews,
      [fieldId]: {
        previewUrl,
        fileName: file.name,
        contentType: file.type || 'application/octet-stream',
        sizeBytes: file.size,
      },
    })
    void (async () => {
      try {
        previewDataUrl = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : previewUrl)
          reader.onerror = () => reject(reader.error ?? new Error('Failed to read file preview'))
          reader.readAsDataURL(file)
        })
        setFilePreviews((prev) => ({
          ...prev,
          [fieldId]: {
            ...(prev[fieldId] ?? {
              previewUrl,
              fileName: file.name,
              contentType: file.type || 'application/octet-stream',
              sizeBytes: file.size,
            }),
            previewDataUrl,
          },
        }))
        persistDriverRegistrationDraftSnapshot(answers, files, {
          ...filePreviews,
          [fieldId]: {
            previewUrl,
            previewDataUrl,
            fileName: file.name,
            contentType: file.type || 'application/octet-stream',
            sizeBytes: file.size,
          },
        })
      } catch {
        // If the data URL cannot be created, keep the object URL preview.
      }
    })()
    setUploadingFields((prev) => ({ ...prev, [fieldId]: true }))
    try {
      const uploaded = await uploadDriverApplicationFile(file)
      console.info('[DriverRegistration] upload success', {
        fieldId,
        fileName: uploaded.fileName,
        objectKey: uploaded.objectKey,
        contentType: uploaded.contentType,
        sizeBytes: uploaded.sizeBytes,
      })
      setFiles((prev) => ({
        ...prev,
        [fieldId]: {
          objectKey: uploaded.objectKey,
          fileName: uploaded.fileName,
          contentType: uploaded.contentType,
          sizeBytes: uploaded.sizeBytes,
          fileUrl: uploaded.fileUrl,
        },
      }))
      const nextFiles = {
        ...files,
        [fieldId]: {
          objectKey: uploaded.objectKey,
          fileName: uploaded.fileName,
          contentType: uploaded.contentType,
          sizeBytes: uploaded.sizeBytes,
          fileUrl: uploaded.fileUrl,
        },
      }
      setFilePreviews((prev) => {
        const nextPreviews = {
          ...prev,
          [fieldId]: {
            previewUrl,
            previewDataUrl,
            fileName: file.name,
            contentType: file.type || 'application/octet-stream',
            sizeBytes: file.size,
          },
        }
        persistDriverRegistrationDraftSnapshot(answers, nextFiles, nextPreviews)
        return nextPreviews
      })
      setFieldErrors((prev) => {
        const next = { ...prev }
        delete next[fieldId]
        return next
      })
    } catch (error) {
      setFieldErrors((prev) => ({
        ...prev,
        [fieldId]: error instanceof Error ? error.message : t('common.error'),
      }))
    } finally {
      setUploadingFields((prev) => {
        const next = { ...prev }
        delete next[fieldId]
        return next
      })
    }
  }

  if (!session.isReady || isLoading) {
    return (
      <div className="min-h-[100dvh] flex items-center justify-center bg-white user-safe-top user-safe-bottom">
        <p className="text-sm text-muted">{t('common.loading')}</p>
      </div>
    )
  }

  if (application?.status === 'pending') {
    return (
      <div className="min-h-[100dvh] bg-white user-safe-top user-safe-bottom px-5 py-4">
        <button type="button" onClick={() => navigate('/profile')} className="touch-compact inline-flex items-center gap-2 text-sm font-semibold text-black">
          <ArrowLeft size={18} />
          {t('common.close')}
        </button>
        <div className="mt-10 flex flex-col items-center text-center px-4">
          <div className="w-16 h-16 rounded-2xl bg-surface border border-border flex items-center justify-center">
            <Clock size={32} weight="duotone" className="text-black" />
          </div>
          <h1 className="text-xl font-black text-black mt-5">{t('driverRegistration.pendingTitle')}</h1>
          <p className="text-sm text-muted mt-2">{t('driverRegistration.pendingDescription')}</p>
        </div>
      </div>
    )
  }

  if (application?.status === 'approved') {
    return (
      <div className="min-h-[100dvh] bg-white user-safe-top user-safe-bottom px-5 py-4">
        <button type="button" onClick={() => navigate('/profile')} className="touch-compact inline-flex items-center gap-2 text-sm font-semibold text-black">
          <ArrowLeft size={18} />
          {t('common.close')}
        </button>
        <div className="mt-10 flex flex-col items-center text-center px-4">
          <div className="w-16 h-16 rounded-2xl bg-surface border border-border flex items-center justify-center">
            <CheckCircle size={32} weight="duotone" className="text-emerald-600" />
          </div>
          <h1 className="text-xl font-black text-black mt-5">{t('driverRegistration.approvedTitle')}</h1>
          <p className="text-sm text-muted mt-2">{t('driverRegistration.approvedDescription')}</p>
          <button
            type="button"
            onClick={() => void handleOpenDriverCabinet()}
            disabled={isEnteringDriverCabinet}
            className="mt-6 h-11 px-5 rounded-pill bg-black text-white text-sm font-bold disabled:opacity-60"
          >
            {isEnteringDriverCabinet
              ? t('driverRegistration.openingCabinet', { defaultValue: 'Opening...' })
              : t('driverRegistration.openCabinet')}
          </button>
        </div>
      </div>
    )
  }

  const showRejectedBanner = application?.status === 'rejected'

  return (
    <div className="min-h-[100dvh] bg-white user-safe-top user-safe-bottom">
      <div className="sticky top-0 z-10 bg-white/95 backdrop-blur border-b border-border px-5 py-3 flex items-center justify-between gap-3">
        <button type="button" onClick={() => navigate(-1)} className="touch-compact inline-flex items-center gap-2 text-sm font-semibold text-black">
          <ArrowLeft size={18} />
          {t('common.close')}
        </button>
        <LanguageSwitcher
          variant="header"
          onChangeLanguage={async (language) => {
            await updateCurrentUserLanguage(language)
          }}
        />
      </div>

      <div className="px-5 py-5 pb-8 max-w-lg mx-auto">
        <h1 className="text-2xl font-black text-black">{t('driverRegistration.title')}</h1>
        {introText && <p className="text-sm text-muted mt-2">{introText}</p>}

        {showRejectedBanner && (
          <div className="mt-4 rounded-card border border-red-200 bg-red-50 p-4">
            <div className="flex items-start gap-3">
              <SealWarning size={20} className="text-red-600 flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-bold text-red-700">{t('driverRegistration.rejectedTitle')}</p>
                {application.rejectionReason && (
                  <p className="text-sm text-red-600 mt-1">{application.rejectionReason}</p>
                )}
                <p className="text-xs text-red-600/80 mt-2">{t('driverRegistration.resubmitHint')}</p>
              </div>
            </div>
          </div>
        )}

        <form
          onSubmit={(event) => {
            event.preventDefault()
            if (!isSubmitting && schema && Object.keys(uploadingFields).length === 0) void handleSubmit()
          }}
        >
        <div className="mt-6 space-y-5">
          {sortedFields.map((field) => (
            <DynamicFormField
              key={field.id}
              field={field}
              language={i18n.language}
              value={answers[field.id] ?? ''}
              fileEntry={files[field.id] ?? null}
              previewUrl={filePreviews[field.id]?.previewUrl ?? null}
              previewDataUrl={filePreviews[field.id]?.previewDataUrl ?? null}
              previewFileName={filePreviews[field.id]?.fileName ?? null}
              selectedFileName={selectedFileNames[field.id] ?? null}
              previewContentType={filePreviews[field.id]?.contentType ?? null}
              previewSizeBytes={filePreviews[field.id]?.sizeBytes ?? null}
              error={fieldErrors[field.id]}
              disabled={isSubmitting}
              isUploading={Boolean(uploadingFields[field.id])}
              onValueChange={(value) => {
                const nextAnswers = { ...answers, [field.id]: value }
                setAnswers(nextAnswers)
                persistDriverRegistrationDraftSnapshot(nextAnswers, files, filePreviews)
                setFieldErrors((prev) => {
                  const next = { ...prev }
                  delete next[field.id]
                  return next
                })
              }}
              onFileSelect={(file) => handleFileSelect(field.id, file)}
              onFileError={(message) => setFieldErrors((prev) => ({ ...prev, [field.id]: message }))}
              onFileClear={() => {
                const nextFiles = { ...files }
                delete nextFiles[field.id]
                const nextPreviews = { ...filePreviews }
                delete nextPreviews[field.id]
                const nextSelectedFileNames = { ...selectedFileNames }
                delete nextSelectedFileNames[field.id]
                setFiles(nextFiles)
                setFilePreviews(nextPreviews)
                setSelectedFileNames(nextSelectedFileNames)
                persistDriverRegistrationDraftSnapshot(answers, nextFiles, nextPreviews)
              }}
            />
          ))}
        </div>

        {errorMessage && <p className="text-sm text-red-600 mt-4">{errorMessage}</p>}

        <button
          type="submit"
          disabled={isSubmitting || !schema || Object.keys(uploadingFields).length > 0}
          className="mt-6 w-full h-12 rounded-pill bg-black text-white text-sm font-bold disabled:opacity-50 hover:bg-zinc-800 transition-colors"
        >
          {isSubmitting ? t('driverRegistration.submitting') : t('driverRegistration.submit')}
        </button>
        </form>
      </div>
    </div>
  )
}
