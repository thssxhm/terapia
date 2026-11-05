# Terapia — app Android + site

App para psicólogos e pacientes (Kotlin + Jetpack Compose) com backend no Supabase e site em Next.js na Vercel.

```
terapia/
├── android/      → app Android (abra esta pasta no Android Studio)
├── supabase/     → banco de dados (SQL) e Edge Functions (convite, push, assinaturas)
├── web/          → site (Vercel): início, privacidade, termos, excluir conta, /baixar
└── docs/         → ícone 512×512 para a Play Store
```

## Como funciona (arquitetura)

| Peça | Ferramenta | Para quê |
|---|---|---|
| Login | Supabase Auth | e-mail + código de 6 dígitos, senha, Google; biometria protege a abertura do app |
| Dados | Supabase Postgres + RLS | cada psicólogo só vê os próprios pacientes (regras no banco) |
| Anexos | Supabase Storage | PDF/DOC até 20 MB, bucket privado |
| Chat e pop-up ao vivo | Supabase Realtime | mensagens e emergências aparecem na hora |
| Push | Firebase Cloud Messaging | notificações com o app fechado |
| E-mail do convite | Resend | código + link da Play Store |
| Assinaturas | Google Play Billing 8 | planos `plano_10` … `plano_50` |
| Lembretes | WorkManager | sessão, sininho do psicólogo e humor do dia |

---

## Passo a passo

### 1. Supabase (banco)
1. Crie um projeto em https://supabase.com (região São Paulo).
2. **SQL Editor** → cole e rode `supabase/migrations/20261105000000_init.sql`.
3. **Database → Extensions** → ative `pg_cron` e rode:
   `select cron.schedule('estender-agenda', '0 3 * * 1', 'select public.extend_all_sessions()');`
4. **Authentication → Providers**: deixe *Email* ativo; ative *Google* (passo 3).
5. **Authentication → Email Templates → Magic Link**: troque o conteúdo para mostrar o código:
   `Seu código de acesso ao Terapia é: <b>{{ .Token }}</b>`
6. **Authentication → Settings**: "OTP Expiry" = 3600.
7. Para produção configure SMTP próprio (o envio padrão do Supabase é limitado).
8. Copie *Project URL* e *anon key* (Settings → API).

### 2. Firebase (push)
1. https://console.firebase.google.com → novo projeto → adicionar app Android com o pacote `app.terapia.mobile`.
2. Baixe `google-services.json` e coloque em `android/app/`.
3. Configurações do projeto → Contas de serviço → **Gerar nova chave privada** (JSON).

### 3. Login com Google
1. No Google Cloud (mesmo projeto do Firebase) → APIs e serviços → Credenciais:
   - crie um ID do cliente OAuth **Android** (pacote + SHA-1 do seu certificado — no Android Studio: `./gradlew signingReport`);
   - crie um ID do cliente OAuth **Aplicativo da Web** → este vai em `GOOGLE_WEB_CLIENT_ID` e no Supabase (Providers → Google → Client IDs).

### 4. Edge Functions
Instale a CLI (https://supabase.com/docs/guides/cli) e rode dentro da pasta `terapia/`:

```bash
supabase link --project-ref SEU_PROJECT_REF
```

```bash
supabase functions deploy invite-patient
```

```bash
supabase functions deploy push --no-verify-jwt
```

```bash
supabase functions deploy verify-purchase
```

Secrets (Dashboard → Edge Functions → Secrets):

| Nome | Valor |
|---|---|
| `RESEND_API_KEY` | chave de https://resend.com |
| `EMAIL_FROM` | ex.: `Terapia <convite@seudominio.com.br>` (domínio verificado na Resend) |
| `PLAY_STORE_URL` | `https://play.google.com/store/apps/details?id=app.terapia.mobile` |
| `GOOGLE_SERVICE_ACCOUNT_JSON` | o JSON inteiro da conta de serviço do passo 2 |
| `ANDROID_PACKAGE_NAME` | `app.terapia.mobile` |
| `WEBHOOK_SECRET` | uma senha aleatória longa |

Depois: **Database → Webhooks → Create** → tabela `notifications`, evento `INSERT`, tipo *Supabase Edge Function* `push`, header `x-webhook-secret` = o mesmo `WEBHOOK_SECRET`.

### 5. App Android
1. Copie `android/secrets.properties.example` para `android/secrets.properties` e preencha.
2. Abra a pasta `android/` no Android Studio → aguarde o Gradle sincronizar → ▶ Run.
3. Teste: crie conta de psicólogo → cadastre um paciente com outro e-mail → em outro celular/emulador entre como paciente com o código.

### 6. Play Console
- **Monetizar → Assinaturas**: crie `plano_10`, `plano_20`, `plano_30`, `plano_40`, `plano_50`, cada um com plano base mensal.
- Vincule a conta de serviço (Usuários e permissões → convidar o e-mail da conta de serviço com permissão de "Ver dados financeiros" e "Gerenciar pedidos e assinaturas").
- **Conteúdo do app**: política de privacidade = `https://SEU-SITE/privacidade`; exclusão de conta = `https://SEU-SITE/excluir-conta`; Segurança dos dados: declare dados de saúde, mensagens, e-mail e nome (criptografados em trânsito, exclusão disponível).
- Ícone: `docs/play-store-icon-512.png`.

### 7. Site (Vercel)
Edite `web/site.config.ts` (**e-mail de contato real** e responsável) antes de enviar o app à Play. Para atualizar, publique novamente pela Vercel (ou conecte a pasta `web/` a um repositório GitHub na Vercel).

### 8. Terapia Desktop (painel web pago — R$ 14,99/mês pelo Stripe)

O painel fica em `https://SEU-SITE/painel`. Fluxo: o psicólogo entra → 7 dias grátis (sem cartão, uma vez por conta) → depois paga pelo Stripe → o **webhook** confirma o pagamento e libera o acesso sozinho. Se cancelar ou o cartão falhar, o acesso é bloqueado automaticamente.

1. **Banco:** rode `supabase/migrations/20261106000000_desktop.sql` no SQL Editor do Supabase.
2. **Supabase → Authentication → URL Configuration:** em *Site URL* coloque `https://terapia-pi.vercel.app` e em *Redirect URLs* adicione `https://terapia-pi.vercel.app/painel` (necessário para o login com Google no navegador).
3. **Stripe** (https://dashboard.stripe.com — comece no **modo de teste**):
   - Crie a conta (pessoa física com CPF é aceita) e preencha os dados bancários para receber.
   - **Catálogo de produtos → Adicionar produto:** "Terapia Desktop", preço **recorrente mensal de R$ 14,99** (BRL). Copie o **ID do preço** (`price_...`).
   - **Configurações → Faturamento → Portal do cliente:** ative "Cancelar assinaturas" e "Atualizar forma de pagamento" e salve.
   - **Desenvolvedores → Webhooks → Adicionar destino:** URL `https://terapia-pi.vercel.app/api/stripe/webhook`, eventos: `checkout.session.completed`, `customer.subscription.created`, `customer.subscription.updated`, `customer.subscription.deleted`, `customer.subscription.paused`, `customer.subscription.resumed`, `invoice.paid`, `invoice.payment_failed`. Copie o **segredo de assinatura** (`whsec_...`).
   - **Desenvolvedores → Chaves de API:** copie a **chave secreta** (`sk_test_...` no teste, `sk_live_...` em produção).
4. **Vercel → projeto terapia → Settings → Environment Variables** (ambiente *Production*):

   | Nome | Valor |
   |---|---|
   | `NEXT_PUBLIC_SUPABASE_URL` | Project URL do Supabase |
   | `NEXT_PUBLIC_SUPABASE_ANON_KEY` | chave anon do Supabase |
   | `SUPABASE_SERVICE_ROLE_KEY` | chave service_role do Supabase (**secreta**) |
   | `STRIPE_SECRET_KEY` | `sk_test_...` / `sk_live_...` |
   | `STRIPE_WEBHOOK_SECRET` | `whsec_...` |
   | `STRIPE_PRICE_ID` | `price_...` |
   | `NEXT_PUBLIC_SITE_URL` | `https://terapia-pi.vercel.app` |

   Depois **publique de novo** (Deployments → ⋯ → Redeploy): as variáveis `NEXT_PUBLIC_*` só entram no site em uma nova publicação.
5. **Teste** com o cartão `4242 4242 4242 4242`, qualquer data futura e qualquer CVC. Confira em Stripe → Webhooks se os eventos voltaram com "200".
6. **Produção:** repita o passo 3 no modo *live* (produto, webhook e chaves são separados) e troque as variáveis na Vercel.

---

## Pontos de atenção
- **Política da Google Play:** não coloque dentro do app Android links ou textos chamando para pagar o desktop no site. A Play proíbe direcionar o usuário para pagamentos fora dela. Divulgue o desktop pelo site, por e-mail etc.
- **Anúncios (AdMob):** este app **não** tem anúncios de propósito — ver a seção "AdMob" na conversa/README de entrega.
- O psicólogo deve guardar os registros por 5 anos (Res. CFP 001/2009): exporte o PDF antes de excluir paciente/conta.
- Ao excluir conta de psicólogo, os arquivos do Storage ficam órfãos; limpe periodicamente pelo painel (Storage → attachments) ou crie uma função de limpeza.
- Teste grátis: 10 pacientes por 14 dias (`profiles.trial_until`). Para liberar manualmente um plano de teste: `update profiles set patient_limit = 20 where email = '...';`
