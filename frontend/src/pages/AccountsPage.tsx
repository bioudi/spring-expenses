import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Landmark } from 'lucide-react'
import { useI18n } from '@/i18n'

export default function AccountsPage() {
  const { t } = useI18n()

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Landmark className="h-6 w-6 text-muted-foreground" />
        <h1 className="text-2xl font-bold tracking-tight">{t('nav.accounts')}</h1>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>{t('nav.accounts')}</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">{t('common.noData')}</p>
        </CardContent>
      </Card>
    </div>
  )
}