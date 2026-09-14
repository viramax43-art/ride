import { FileArrowUp, X } from '@phosphor-icons/react'
import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { isImageFile, resolveApplicationFileUrl } from '../../../lib/driverApplicationDisplay'
import { resolveApiBaseUrl } from '../../../config/env'
import { resolveDriverFormText } from '../../../lib/driverFormText'
import type { DriverApplicationFileEntry, DriverRegistrationFormField } from '../../../types'

const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024

type DynamicFormFieldProps = {
  field: DriverRegistrationFormField
  language: string
  value: string
  fileEntry: DriverApplicationFileEntry | null
  previewUrl?: string | null
  previewDataUrl?: string | null
  previewFileName?: string | null
  selectedFileName?: string | null
  previewContentType?: string | null
  previewSizeBytes?: number | null
  error?: string | null
  disabled?: boolean
  isUploading?: boolean
  onValueChange: (value: string) => void
  onFileSelect: (file: File) => Promise<void>
  onFileClear: () => void
  onFileError?: (message: string) => void
}

export function DynamicFormField({
  field,
  language,
  value,
  fileEntry,
  previewUrl,
  previewDataUrl,
  previewFileName,
  selectedFileName,
  previewContentType,
  previewSizeBytes,
  error,
  disabled,
  isUploading,
  onValueChange,
  onFileSelect,
  onFileClear,
  onFileError,
}: DynamicFormFieldProps) {
  const { t } = useTranslation()
  const inputRef = useRef<HTMLInputElement>(null)
  const label = resolveDriverFormText(field.label, language)
  const placeholder = resolveDriverFormText(field.placeholder, language)
  const helpText = resolveDriverFormText(field.helpText, language)

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    console.info('[DriverRegistration] file selected', {
      fieldId: field.id,
      fieldType: field.type,
      fileName: file.name,
      fileType: file.type,
      fileSize: file.size,
    })
    if (file.size > MAX_FILE_SIZE_BYTES) {
      onFileError?.(t('driverRegistration.fileTooLarge'))
      return
    }
    try {
      await onFileSelect(file)
    } catch (error) {
      onFileError?.(error instanceof Error ? error.message : t('common.error'))
    }
  }

  if (field.type === 'textarea') {
    return (
      <label className="block">
        <span className="text-sm font-bold text-black">
          {label}
          {field.required && <span className="text-red-500 ml-1">*</span>}
        </span>
        <textarea
          value={value}
          disabled={disabled}
          placeholder={placeholder}
          onChange={(event) => onValueChange(event.target.value)}
          rows={4}
          className="mt-2 w-full rounded-xl border-[1.5px] border-border bg-surface px-3 py-2.5 text-sm text-black outline-none focus:border-black"
        />
        {helpText && <p className="text-xs text-muted mt-1.5">{helpText}</p>}
        {error && <p className="text-xs text-red-600 mt-1.5">{error}</p>}
      </label>
    )
  }

  if (field.type === 'file') {
    const hasServerFile = Boolean(fileEntry)
    const hasLocalPreview = Boolean(previewUrl && previewFileName)
    const effectiveName = fileEntry?.fileName ?? selectedFileName ?? previewFileName ?? label
    const effectiveSize = fileEntry?.sizeBytes ?? previewSizeBytes ?? 0
    const effectiveContentType = fileEntry?.contentType ?? previewContentType ?? ''
    const resolvedFileUrl = previewDataUrl ?? previewUrl ?? (fileEntry ? resolveApplicationFileUrl(fileEntry.fileUrl, resolveApiBaseUrl()) : '')
    const previewableImage = Boolean(resolvedFileUrl) && isImageFile(effectiveContentType, effectiveName)

    return (
      <div>
        <p className="text-sm font-bold text-black">
          {label}
          {field.required && <span className="text-red-500 ml-1">*</span>}
        </p>
        <input
          ref={inputRef}
          type="file"
          data-ride-file-input="1"
          accept={field.accept || 'image/*,application/pdf'}
          className="hidden"
          disabled={disabled}
          onChange={(event) => void handleFileChange(event)}
        />
        {hasServerFile || hasLocalPreview ? (
          <div className="mt-2 space-y-2">
            <button
              type="button"
              disabled={disabled}
              onClick={() => inputRef.current?.click()}
              className="w-full overflow-hidden rounded-xl border border-border bg-surface text-left transition-colors hover:border-black/30"
            >
              {previewableImage ? (
                <img
                  src={resolvedFileUrl}
                  alt={effectiveName}
                  className="block h-40 w-full object-cover bg-white"
                />
              ) : (
                <div className="flex h-28 items-center justify-center bg-white px-4">
                  <div className="text-center">
                    <p className="text-sm font-semibold text-black truncate max-w-full">{effectiveName}</p>
                    <p className="text-xs text-muted mt-1">{(effectiveSize / 1024).toFixed(0)} KB</p>
                  </div>
                </div>
              )}
              <div className="flex items-center justify-between gap-3 px-3 py-2.5">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-black truncate">{effectiveName}</p>
                  <p className="text-xs text-muted">
                    {(effectiveSize / 1024).toFixed(0)} KB
                  </p>
                </div>
                <span className="flex-shrink-0 text-xs font-semibold text-black">{t('common.change', { defaultValue: 'Change' })}</span>
              </div>
            </button>
            <p className="text-xs text-muted px-1">
              {selectedFileName || previewFileName || fileEntry?.fileName
                ? `Selected file: ${selectedFileName || previewFileName || fileEntry?.fileName}`
                : ''}
            </p>
            <button
              type="button"
              disabled={disabled}
              onClick={onFileClear}
              className="touch-compact inline-flex items-center gap-2 rounded-xl border border-border bg-white px-3 py-2 text-xs font-semibold text-black"
              aria-label={t('common.delete')}
            >
              <X size={16} />
              {t('common.delete')}
            </button>
          </div>
        ) : isUploading ? (
          <div className="mt-2 w-full rounded-xl border border-dashed border-border bg-surface px-4 py-6 flex flex-col items-center gap-2">
            <div className="w-6 h-6 rounded-full border-2 border-border border-t-black animate-spin" />
            <span className="text-sm font-semibold text-black">
              {t('driverRegistration.uploadingFile', { defaultValue: 'Uploading file...' })}
            </span>
            {(selectedFileName || previewFileName || fileEntry?.fileName) && (
              <p className="text-xs text-muted text-center break-all">
                {`Selected file: ${selectedFileName || previewFileName || fileEntry?.fileName}`}
              </p>
            )}
          </div>
        ) : (
          <button
            type="button"
            disabled={disabled}
            onClick={() => inputRef.current?.click()}
            className="mt-2 w-full rounded-xl border border-dashed border-border bg-surface px-4 py-6 flex flex-col items-center gap-2 transition-colors hover:border-black/30"
          >
            <FileArrowUp size={24} className="text-muted" />
            <span className="text-sm font-semibold text-black">{t('driverRegistration.attachFile')}</span>
            <span className="text-xs text-muted">{t('driverRegistration.fileLimit')}</span>
          </button>
        )}
        {helpText && <p className="text-xs text-muted mt-1.5">{helpText}</p>}
        {error && <p className="text-xs text-red-600 mt-1.5">{error}</p>}
      </div>
    )
  }

  return (
    <label className="block">
      <span className="text-sm font-bold text-black">
        {label}
        {field.required && <span className="text-red-500 ml-1">*</span>}
      </span>
      <input
        value={value}
        disabled={disabled}
        placeholder={placeholder}
        onChange={(event) => onValueChange(event.target.value)}
        className="mt-2 w-full h-11 rounded-xl border-[1.5px] border-border bg-surface px-3 text-sm text-black outline-none focus:border-black"
      />
      {helpText && <p className="text-xs text-muted mt-1.5">{helpText}</p>}
      {error && <p className="text-xs text-red-600 mt-1.5">{error}</p>}
    </label>
  )
}
