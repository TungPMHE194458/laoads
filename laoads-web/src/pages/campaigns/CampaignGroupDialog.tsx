import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { errorMessage } from '@/api/client'
import type { CampaignGroup } from '@/api/types'
import { useSaveCampaignGroup } from '@/features/campaign/api'
import { Field, FormDialog } from '@/components/common/dialogs'
import { Input, Textarea } from '@/components/ui/input'

/** Mô tả nhóm chiến dịch tối đa 500 ký tự (BE GroupRequest / V7). */
export const GROUP_DESCRIPTION_MAX = 500

/**
 * Tạo / sửa nhóm chiến dịch (tên + mô tả). Chỉ mount khi mở — state form khởi tạo từ `group` mỗi lần mở.
 * `onSaved` nhận nhóm vừa lưu (wizard dùng để chọn luôn nhóm mới).
 */
export function CampaignGroupDialog({ group, onOpenChange, onSaved }: { group?: CampaignGroup | null; onOpenChange: (o: boolean) => void; onSaved?: (g: CampaignGroup) => void }) {
  const { t } = useTranslation()
  const save = useSaveCampaignGroup()
  const [name, setName] = React.useState(group?.name ?? '')
  const [description, setDescription] = React.useState(group?.description ?? '')
  return (
    <FormDialog
      open
      onOpenChange={onOpenChange}
      title={group ? t('campaign.groupDialog.editTitle') : t('campaign.wizard.newGroup')}
      submitLabel={group ? t('common.save') : t('campaign.wizard.createGroup')}
      loading={save.isPending}
      onSubmit={() => {
        if (!name.trim()) return
        save.mutate(
          { id: group?.id, name: name.trim(), description: description.trim() || null },
          {
            onSuccess: (g) => {
              toast.success(group ? t('campaign.groupDialog.saved') : t('campaign.wizard.groupCreated'))
              onSaved?.(g)
              onOpenChange(false)
            },
            onError: (e) => toast.error(errorMessage(e)),
          },
        )
      }}
    >
      <Field label={t('campaign.wizard.groupName')} required>
        <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={255} autoFocus />
      </Field>
      <Field label={t('campaign.groupDialog.description')} hint={<span className="tabular">{t('campaign.wizard.charCounter', { n: description.length, max: GROUP_DESCRIPTION_MAX })}</span>}>
        <Textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={GROUP_DESCRIPTION_MAX} placeholder={t('campaign.groupDialog.descriptionPh')} />
      </Field>
    </FormDialog>
  )
}
