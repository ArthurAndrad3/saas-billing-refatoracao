# Relatório Técnico — Refatoração do SaaS Billing Co.

**Disciplina:** Implantação e Entrega de Software — 2026/2 (INF/UFG)
**Professora:** Sofia Costa Paiva
**Aluno:** Arthur Fernandes (trabalho individual)
**Repositório:** https://github.com/ArthurAndrad3/saas-billing-refatoracao

## Como executar

```bash
mvn test
```

Requer Java 21+ e Maven. A suíte não depende de banco de dados nem de servidor SMTP. O mesmo comando roda no GitHub Actions a cada push ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)).

---

## 1. Diagnóstico do débito técnico

As linhas se referem ao arquivo original, `SubscriptionBillingService.java` no [commit 1](https://github.com/ArthurAndrad3/saas-billing-refatoracao/blob/32589bb/src/main/java/com/saas/billing/SubscriptionBillingService.java).

| # | Trecho | Code smell / princípio violado | "Juros" (impacto na manutenção e na entrega contínua) | Resolvido em |
|---|---|---|---|---|
| 1 | L18–105 (`processMonthlyBilling`) | **Long Method / God Method**: 88 linhas, complexidade ciclomática 18 | Qualquer ajuste de regra exige entender o método inteiro. Revisões de PR ficam lentas e o risco de regressão em cada deploy é alto. | Commit 4 |
| 2 | L12–105 (classe inteira) | **Violação do SRP**: calcula preço, consulta cliente, cobra cartão, grava fatura e envia e-mail | A classe tem pelo menos 4 motivos para mudar. Mudanças em times diferentes (financeiro, infra, comunicação) disputam o mesmo arquivo e geram conflitos de merge. | Commits 3 e 4 |
| 3 | L18–20 | **Long Parameter List** (8 parâmetros) e **Primitive Obsession** na entrada (`String signupDateStr`, `double basePrice`, `boolean isNonProfit`) | Trocar a ordem de dois `String` ou `boolean` compila sem erro e gera cobrança errada. Adicionar um campo quebra todos os chamadores. | Commit 2 |
| 4 | L29–57 | **Primitive Obsession para dinheiro**: valores em `double` sem arredondamento | Erro de ponto flutuante chega ao cliente (ex.: `R$ 76.49999999999999` no log e no e-mail). A regra de arredondamento não existe, então cada ponto que exibir ou gravar o valor decide sozinho. | Commit 2 |
| 5 | L25 | **Dependência temporal estática** (`LocalDate.now()`) | O resultado depende do dia em que o teste roda. O cupom BLACKFRIDAY só é testável em novembro, e os descontos de fidelidade mudam de faixa com o tempo. Testes ficam não determinísticos, e o CI fica intermitente. | Commit 3 |
| 6 | L59, L89–95 | **Dependência direta de I/O / violação do DIP**: `DriverManager` e `SimpleEmail` instanciados dentro da regra de negócio | É impossível executar qualquer teste sem PostgreSQL e SMTP. A esteira de CI não consegue validar o código, e a regra de preço não pode ser reutilizada em outro contexto. | Commit 3 |
| 7 | L100–102 | **Erro mascarado** (`catch (Exception e)` genérico) | Falhas de banco ou SMTP viram uma linha de log e o método retorna normalmente. Uma cobrança aprovada pode ficar sem fatura gravada, sem nenhum alerta. Erros de programação (NPE) também são engolidos. | Commit 3 |
| 8 | L14–16 | **Credenciais hard-coded** (usuário/senha do banco no código) | A senha vai para o repositório. Trocar de ambiente (dev, homologação, produção) exige recompilar, o que inviabiliza promover o mesmo artefato entre ambientes. | Commit 3 |
| 9 | L96–98 | **Código morto com log enganoso**: `emailSender.send()` comentado e log "E-mail disparado" | O sistema diz que enviou e-mail sem enviar. Quem investiga um incidente pelos logs chega a uma conclusão falsa. | Commit 3 |
| 10 | L18 (`planType`) | **Parâmetro morto** (nunca é lido) | Todo chamador precisa informar um valor que não tem efeito. Sugere uma regra por plano que não existe. | Commit 2 |
| 11 | L32–52 | **Magic numbers** (`0.15`, `0.10`, `0.70`, `30.0`, `0.02`, `0.001`) | As taxas não têm nome. Alterar uma delas exige caçar o literal, e não há como saber se `0.10` em dois lugares é a mesma regra. | Commits 2 e 4 |
| 12 | L61–63, L83 | **Vazamento de recursos**: `PreparedStatement` e `ResultSet` sem fechamento | Sob carga, o pool de conexões e cursores do banco se esgota. É uma falha que só aparece em produção. | Commit 3 |

---

## 2. Refatorações aplicadas

| Commit | Refatorações do catálogo (Fowler) | Resultado |
|---|---|---|
| [2](https://github.com/ArthurAndrad3/saas-billing-refatoracao/commit/bcae1ab) | *Replace Primitive with Object*, *Introduce Parameter Object*, *Remove Parameter*, *Replace Magic Literal* | `Money` (record imutável, `BigDecimal`, arredondamento HALF_EVEN em 2 casas só na saída) e `BillingCommand` (1 parâmetro no lugar de 8, data já como `LocalDate`) |
| [3](https://github.com/ArthurAndrad3/saas-billing-refatoracao/commit/099f075) | *Extract Interface*, *Move Function*, *Extract Class* (adaptadores), *Replace Error Code with Exception*, além de injeção de dependência pelo construtor | Portas `CustomerRepository`, `InvoiceRepository`, `PaymentGateway` e `NotificationService`. Adaptadores JDBC/SMTP em `adapter/`. `Clock` injetado. `InfrastructureException` no lugar do `catch` genérico. |
| [4](https://github.com/ArthurAndrad3/saas-billing-refatoracao/commit/743f620) | *Extract Class*, *Extract Function*, *Decompose Conditional*, *Replace Nested Conditional with Guard Clauses* | `PricingCalculator` puro (sem I/O, recebe a data como parâmetro). Orquestrador com 12 linhas. CI no GitHub Actions. |

Sobre a `InvoiceRepository`: o enunciado pede três portas, mas o legado também grava a fatura no banco. Colocar esse INSERT em `CustomerRepository` misturaria duas responsabilidades, por isso ele ganhou uma porta própria.

### Arquitetura final

```
com.saas.billing
├── SubscriptionBillingService   orquestrador (fachada de alto nível)
├── PricingCalculator            regras de preço (domínio puro)
├── Money, BillingCommand        value objects imutáveis
├── Customer, InvoiceStatus      modelo de domínio
├── port/                        interfaces (portas) + InfrastructureException
└── adapter/                     JDBC, SMTP e gateway de pagamento
```

O orquestrador ficou assim:

```java
public List<String> processMonthlyBilling(BillingCommand command) {
    Money amount = pricing.calculate(command, LocalDate.now(clock));
    Optional<Customer> customer = customers.findById(command.customerId());

    if (customer.isEmpty()) {
        return List.of();
    }
    if (!customer.get().isActive()) {
        return List.of("Cliente inativo. Cobrança cancelada: " + command.customerId());
    }
    return charge(customer.get(), command.paymentCardToken(), amount);
}
```

---

## 3. Métricas: antes vs. depois

Ferramentas: [lizard 1.24.3](https://github.com/terryyin/lizard) para tamanho e complexidade ciclomática (CCN) e JaCoCo 0.8.13 para cobertura.

| Métrica | Antes (commit 1) | Depois (commit 4) |
|---|---|---|
| Linhas do método principal | 88 | 12 |
| NLOC do método principal (sem linhas em branco/comentários) | 70 | 11 |
| Complexidade ciclomática do método principal | 18 | 3 |
| Maior complexidade ciclomática do sistema | 18 | 7 (`applyPromoCode`) |
| Complexidade ciclomática média por método | 18 | 2,1 |
| Parâmetros do método principal | 8 | 1 |
| Tipos de produção (classes, records, interfaces, enum) | 1 | 15 |
| Dependências concretas de infraestrutura na regra de negócio | 3 (`DriverManager`, `SimpleEmail`, `LocalDate.now()`) | 0 |
| `catch (Exception e)` genéricos | 1 | 0 |
| Credenciais no código-fonte | 3 | 0 |
| Testes automatizados | 0 | 36 |
| Cobertura de linhas, domínio + aplicação | 0% | 100% |
| Cobertura de linhas, total | 0% | 66,7% |
| Cobertura de ramos, total | 0% | 79,2% |
| Tempo da suíte | não executável sem infraestrutura | < 1 s local; 22 s no GitHub Actions |

Os 33,3% de linhas não cobertas são os três adaptadores de infraestrutura (`JdbcCustomerRepository`, `JdbcInvoiceRepository`, `SmtpNotificationService`). Eles só podem ser testados contra banco e SMTP reais, o que o enunciado proíbe na esteira de CI. O próximo passo seria uma suíte de integração separada, com Testcontainers.

---

## 4. Preservação de comportamento

### Como foi verificado

- **[`LegacyPricingEquivalenceTest`](src/test/java/com/saas/billing/LegacyPricingEquivalenceTest.java)**: guarda uma cópia literal da fórmula original em `double` e compara com o `PricingCalculator` em 100 mil entradas aleatórias (preço, datas, cupons, atraso, ONG). A diferença máxima aceita é 1 centavo.
- **[`PricingCalculatorTest`](src/test/java/com/saas/billing/PricingCalculatorTest.java)**: 17 cenários explícitos, incluindo os limites de 12/13 e 24/25 meses, cupons dentro e fora da condição, piso em zero e a combinação de todas as regras.
- **[`SubscriptionBillingServiceTest`](src/test/java/com/saas/billing/SubscriptionBillingServiceTest.java)**: fluxos de cliente ativo, inativo e inexistente, pagamento aprovado e recusado, e falha de infraestrutura. Usa fakes em memória e `Clock.fixed`.

### Regras preservadas, mesmo parecendo incorretas

Foram mantidas porque são comportamento de negócio existente. Ficam registradas para discussão com a área de negócio:

- A contagem de meses ignora o dia: quem assinou em 31/01 tem 1 mês de casa em 01/02.
- O desconto de fidelidade incide sobre o preço base, e os demais incidem sobre o valor já descontado. A ordem é fidelidade → ONG → cupom → multa e juros → piso zero.
- Os cupons não são combináveis. Um cupom com espaços nas pontas (`" BLACKFRIDAY"`) não é reconhecido.
- Cliente inexistente retorna lista vazia, sem log.
- O e-mail de fatura é enviado mesmo quando o pagamento é recusado, com o mesmo texto.

### Mudanças intencionais

| Mudança | Antes | Depois | Justificativa |
|---|---|---|---|
| Precisão monetária | `double`, sem arredondamento | `BigDecimal` exato, arredondado para 2 casas (HALF_EVEN) | Em 355 de 500 mil casos o legado errava 1 centavo por imprecisão do `double`. Nunca há diferença maior. |
| Formato do valor no log/e-mail | `R$ 76.49999999999999` | `R$ 76.50` | Consequência direta do item anterior. |
| Falha de infraestrutura | Vira a linha `"Erro fatal no processamento: ..."` e o método retorna normalmente | Lança `InfrastructureException` com a causa original | Pedido do enunciado (item 2.4). Quem chama passa a poder fazer retry, alertar ou desfazer. |
| Envio de e-mail | `send()` comentado, mas o log dizia "disparado" | O adaptador SMTP envia de fato | Remove o código morto e torna o log verdadeiro. Nos testes, o envio é substituído por um fake. |
| Conexões com o banco | Uma conexão para consulta e INSERT, sem fechar statements | Uma conexão por operação, todos os recursos fechados | Corrige o vazamento. O legado também não tinha transação, então a garantia de atomicidade é a mesma. |

---

## 5. Histórico do Git

| Commit | Descrição |
|---|---|
| [`32589bb`](https://github.com/ArthurAndrad3/saas-billing-refatoracao/commit/32589bb) | Adição do código legado base |
| [`bcae1ab`](https://github.com/ArthurAndrad3/saas-billing-refatoracao/commit/bcae1ab) | Introdução dos Value Objects e remoção de Primitive Obsession |
| [`099f075`](https://github.com/ArthurAndrad3/saas-billing-refatoracao/commit/099f075) | Extração das interfaces de infraestrutura e injeção de dependência |
| [`743f620`](https://github.com/ArthurAndrad3/saas-billing-refatoracao/commit/743f620) | Refatoração do motor de precificação e validação da suíte verde |

Este relatório foi adicionado num commit separado, depois dos quatro commits da refatoração.
