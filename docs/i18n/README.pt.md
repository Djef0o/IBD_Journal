# IBD Journal

[English](../../README.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [Italiano](README.it.md) · **Português**

Um diário Android para pessoas que vivem com doença de Crohn ou retocolite ulcerativa. Registre evacuações e sintomas em poucos toques, acompanhe seus tratamentos e resultados de exames, prepare seus exames e leve um relatório claro para o seu médico.

Os seus dados de saúde ficam no seu celular.

<p>
  <img src="../screenshots/home.png" width="200" alt="Tela Hoje">
  <img src="../screenshots/history.png" width="200" alt="Calendário do histórico">
  <img src="../screenshots/trends.png" width="200" alt="Tendências">
  <img src="../screenshots/care.png" width="200" alt="Aba Cuidados">
</p>

## Funcionalidades

### Diário diário
- **Evacuações em dois toques**: escala de Bristol (tipo 0 = nenhuma evacuação naquele dia), sangue, muco, dor e cansaço (0–5), urgência, evacuações noturnas.
- **Falsa vontade**: vontade de evacuar sem eliminar fezes, comum durante crises. Registrada separadamente e nunca contada como evacuação.
- **Refeições**: foto ou leitura de código de barras (dados do produto pelo Open Food Facts).
- **Lembrete noturno**: uma notificação para registrar o tipo de evacuação, a dor e o cansaço sem abrir o aplicativo. Ela permanece até que as três perguntas sejam respondidas.

<img src="../screenshots/notification.png" width="400" alt="Notificação noturna interativa">

### Histórico e tendências
- Calendário mensal colorido pela consistência das fezes.
- Evacuações por dia, 30 dias / 90 dias / todo o histórico, e dias com cada sintoma comparados ao período anterior.
- **Relatório para o médico (PDF)**: resumo do período, sintomas, tratamentos com adesão e últimos exames de sangue com as faixas de referência do laboratório.

### Cuidados
- **Tratamentos**: medicamentos usuais para DII, lembretes, "tomado / esquecido" pela notificação, adesão e alertas de reposição.
- **Exames de sangue e de fezes** agendados com lembretes.
- **Colonoscopia e retossigmoidoscopia**: data do exame, dieta sem resíduos e doses do preparo intestinal conforme prescritas pelo seu médico, consulta de anestesia. Um lembrete para cada etapa.
- **Resultados de exames**: 43 exames comuns (PCR, calprotectina fecal, hemograma, ferro, rins, fígado, lipídios…), com a faixa de referência impressa pelo seu laboratório, gráficos e uma tabela dos exames de sangue. O aplicativo não fornece valores normais próprios.
- **Importação de laudos** (PDFs de laboratório, vários de uma vez): o texto do PDF é lido no celular, os valores são convertidos para as unidades do aplicativo, e nada é salvo antes da sua revisão. Documentos digitalizados não são suportados.

<p>
  <img src="../screenshots/labs.png" width="200" alt="Resultados por exame">
  <img src="../screenshots/lab-panels.png" width="200" alt="Tabela de exames de sangue">
  <img src="../screenshots/lab-chart.png" width="200" alt="Gráfico de PCR com a faixa do laboratório">
</p>

### Também
- Lembretes como notificações do aplicativo ou como eventos no calendário do celular.
- Backup e restauração criptografados por senha, em um arquivo que você mesmo guarda.
- Primeira abertura guiada, temas claro e escuro, várias paletas de cores.
- Disponível em inglês, francês, alemão, espanhol, italiano e português.

## Privacidade

- O diário, os tratamentos e os resultados de exames são armazenados em um banco de dados local no celular. Não há conta nem servidor do aplicativo.
- Sem serviços do Google e sem rastreadores: os laudos são lidos a partir do próprio texto do PDF (PdfBox-Android) e os códigos de barras são lidos com ZXing, tudo no celular.
- O acesso à rede é usado apenas para consultas de código de barras (Open Food Facts). Sem backup na nuvem: o backup automático do Google no Android está desativado para o app.
- As exportações são criptografadas com uma senha que você escolhe (AES-256-GCM, derivação de chave PBKDF2).
- A sincronização de calendário vem desativada por padrão. Ao ativá-la, os nomes de tratamentos e exames são gravados no calendário do celular, que pode ser sincronizado com uma conta online; o aplicativo avisa antes de ativar.

## Compilação

Requisitos: Android Studio (versão recente), JDK 17, Android SDK 37. O aplicativo roda no Android 8.0 (API 26) e superiores.

```bash
./gradlew :app:installDebug          # compila e instala em um dispositivo conectado
./gradlew :shared:testAndroidHostTest # testes unitários do módulo shared
```

A assinatura de release lê `keystore.properties` (não versionado).

## Estrutura do projeto

| Módulo | Conteúdo |
|---|---|
| `shared/` | Kotlin Multiplatform: modelos de dados, banco de dados Room, análises, leitor de laudos, cronogramas de lembretes e exames. Compila para Android e iOS. |
| `app/` | Aplicativo Android: interface Jetpack Compose (Material 3), Hilt, notificações, sincronização de calendário, relatório em PDF, extração de texto de PDF (PdfBox-Android), leitura de código de barras (ZXing). |

## Aviso legal

IBD Journal é um diário pessoal. Ele registra e exibe o que você informa; não interpreta seus resultados nem sugere qualquer decisão médica, e não é um dispositivo médico. Ele não substitui a orientação médica: siga sempre as instruções do seu médico, especialmente para o preparo dos exames e para os tratamentos.

## Licença

[GNU AGPL-3.0](../../LICENSE). Você pode usar, estudar, modificar e compartilhar o app; quem distribuir uma versão modificada, ou a oferecer como serviço online, deve publicar o código-fonte sob a mesma licença.
