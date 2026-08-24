# HCIN: Human-Centric Intelligence Network

## Definição:

A sociabilidade dos seres humanos se dá por diferentes relações, sendo algumas guiadas por interesses comuns, interesses financeiros, emocionais etc. A linguagem desempenha um papel fundamental em como essas relações se estabelecem e como tais interesses são demandados e satisfeitos. Enquanto a Web-Ontology pode ser útil para explicar os interesses por trás das relações, ela parece ser necessária mas insuficeiente em demonstrar a importância e a topologia das relações. Além da explicabilidade trazida pela web ontology no contexto apresentado, uma visão gráfica das pessoas, suas relações e interesses, poderia ser útil para entender melhor a sociabilidade humana, reforçar os vínculos, bem como mitigar relações improdutivas.

Apoiado neste raciocínio nasce a ideia de uma topologia de uma rede de inteligência centrada no ser humano, suas relações e interesses (HCIN: Human-Centric Intelligence Network). De certa maneira o HCIN poderia ser entendido como mais um social graph, mas vai além disso, pois permitrá responder perguntas como:

- Quão importante é B para A?
- Em qual contexto?
- Essa relação é recíproca?
- Ela está aumentando ou diminuindo?
- É principalmente afetiva, financeira ou profissional?
- A confiança deriva de quê?
- Há dependência?
- Há interesses alinhados?
- Há interesses conflitantes?
- Qual foi a evidência para o sistema concluir isso?
- Essa relação é importante para qual objetivo?

Perguntas estas que um social graph não é capaz de resolver. Como dito, o HCIN é uma rede centrada no ser humano, uma rede semantica, temporal, multicamada e rastreável, que representa pessoas, seus relacionamentos, interesses, contextos e evidencias sobre como estes relacionamentos são entendidos. Conceitualmente, a proposta do HCIN é, apoiado na estrutura RDF-OWL de explicabilidade sobre “o que existe e como as coisas se relacionam?”, evoluir para  “qual é a estrutura, a natureza, a relevância e a dinâmica dessas relações para um ser humano?”, onde Ontologia e Topologia nao são concorrentes, mas complementares.

## Demandas do HCIN:

Para que os conceitos aqui apresentados algumas demandas precisam ser solucionadas, entre elas: 

### Suporte a relações assimétricas nas quais: 

A confia em B ≠ B confia em A
A depende de B ≠ B depende de A
A admira B ≠ B admira A

### Entidade própria:

Ao invés de apenas:

:Me :trusts :Joao .

Suportar:

:relationship_981
    a hcin:Relationship ;

    hcin:source :Me ;
    hcin:target :Joao ;

    hcin:type hcin:ProfessionalTrust ;

    hcin:context :SciCrop ;

    hcin:startedAt "2019-03-01"^^xsd:date ;

    hcin:confidence 0.92 ;

    prov:wasDerivedFrom :thought_1234 .
    
Que permitirá representações específicas de:

- source
- target
- type
- context
- time
- evidence
- confidence
- reciprocity
- frequency
- salience
- dependency
- alignment

### Determinação multimodal da força do relacionamento:

Mesmo sendo viável determinar a força de um relacionamento com:

:Me hcin:relationshipStrength [
    hcin:with :Joao ;
    hcin:value 0.87
] .

O valor de 0.87 é insuficiente quando alguém pode ser determinado como:

- proximidade emocional       0.10
- confiança profissional      0.95
- frequência de interação     0.80
- dependência econômica       0.70
- alinhamento intelectual     0.92
- confiança pessoal           0.35

Pois uma mesma pessoa, abriga diversas camadas de relacionamento e interessses:

                 ┌── Family
                 │
                 ├── Friendship
                 |
Person A ────────┼── Professional
                 │
                 ├── Financial
                 │
                 ├── Intellectual
                 │
                 ├── Emotional
                 │
                 └── Governance
                 
Interesses, os quais podem funcionar como nova classe de ligação: Pessoas podem ter relacionamentos temporários apenas por interesses comuns, que forçam a existência de tal relacionamento.

### Suporte a SKOS, FOAF, SIOC, PROV-O e vocab W3C:

FOAF é fornece parte da base de Person, identidade e relações sociais; e inclusive possui foaf:knows e foaf:interest, embora a representação de interesse do FOAF não seja plenamente suficiente para a proposta HCIN. Já o vocab W3C é especialmente útil nos relacionamentos profissionais previsto no HCIN, pois já modela Organization, Membership, Role, memberOf, headOf, estruturas organizacionais e duração de memberships. A própria recomendação sugere a relação n-ária Membership justamente quando é necessário representar uma pessoa que desempenha determinado papel dentro de determinada organização.

O PROV-O tem um caso de uso importante na rastreabilidade: de qual pensamento, documento, conversa ou inferência surgiu uma afirmação. E SIOC + FOAF já têm um histórico de modelagem de comunidades, pessoas e conteúdo social na Web Semântica. 

### on7o como mecanismo de aquisição de conhecimento:

Em uma abordagem bottom-up o on7o serve de base e início como pode ser entendido no diagrama:

                 HCIN
                  │
       ┌──────────┴──────────┐
       │                     │
   Semantics              Topology
       │                     │
 RDF / OWL / SKOS       Graph analysis
       │                     │
       ├─ what?              ├─ how strong?
       ├─ who?               ├─ how central?
       ├─ why?               ├─ how connected?
       └─ what type?         └─ how changing?
             │                     │
             └──────────┬──────────┘
                        │
                   Provenance
                        │
                    PROV-O
                        │
                    Evidence
                        │
               Human thoughts
                        │
                       on7o
                      

## A importância de cada papel:

- O on7o inicia a captura de pensamento
- A Web Ontology é a camada semântica.
- HCIN é a representação e análise sociocognitiva construída sobre esse conhecimento.

Enquanto milhares de pensamentos capturados ao longo dos anos podem ilustrativamente identificar:

Pessoa A
   ├── aparece em 217 pensamentos
   ├── SciCrop aparece em 163 deles
   ├── M&A aparece em 41
   ├── decisões aparece em 72
   └── confiança explícita aparece em 18
   
e:

Pessoa B
   ├── aparece em 92 pensamentos
   ├── família aparece em 74
   ├── educação aparece em 31
   └── SciCrop aparece em 0
   
Mas que ontologicamente poderiam ser reduzidos a apenas:

:A a foaf:Person .
:B a foaf:Person .

Enquanto topologicamente são completamente diferentes.

Mais do que isso, o papel do HCIN agregará recursos complementares a OWL:

- degree centrality
- betweenness centrality
- closeness
- community detection
- structural holes
- bridging relationships
- network density
- reciprocity
- temporal evolution

Por exemplo, o HCIN poderia descobrir:

- João não é uma das pessoas com quem você mais interage, mas é o único elo entre dois clusters profissionais importantes.

Isso é betweenness.

Ou:

- Três pessoas que você trata como contatos distintos pertencem, na realidade, a um cluster extremamente interconectado de interesses relacionados a AgTech.

Isso é topologia.


## Riscos derivados da assimetria em relacionamentos e interesses:

A capacidade do HCIN entender que Pessoa A pode ser ao mesmo tempo, profissionalmente improdutivo e emocionalmente essencial, permitirá identificar desalinhamentos, tensões, dependências e relações de baixa utilidade em relação a objetivos e contextos específicos.


## O princípio da perspectiva:

O HCIN não conhece:

“a relação entre João e Maria.”

Ele conhece coisas como:

“na visão do usuário, João parece confiar em Maria.”

ou:

“João declarou que confia em Maria.”

ou:

“o sistema inferiu, com 0,72 de confiança, que existe colaboração entre João e Maria.”

O que são representações epistemologicamente diferentes, que para serem abarcadas pelo HCIN demandam uma definição conjunta de:

- subject
- predicate
- object

e:

- perspective
- provenance
- confidence
- time
- context
- epistemic status

Com esta complementação a arquitetura do HCIN poderia ser desenhada como um personal knowledge graph:

                    HUMAN
                      │
                  thoughts
                      │
                      ▼
                    on7o
             Thought Acquisition
                      │
                      ▼
                 STT + LLM
                      │
              candidate knowledge
                      │
          ┌───────────┴────────────┐
          │                        │
          ▼                        ▼
      Ontology                 Questions
 RDF / OWL / SKOS                 │
          │                        │
          └──────────┬─────────────┘
                     │
                  clarified
                  knowledge
                     │
                     ▼
                    HCIN
       Human-Centric Intelligence Network
                     │
        ┌────────────┼────────────┐
        │            │            │
     People      Relationships  Interests
        │            │            │
        └────────────┼────────────┘
                     │
                  Topology
                     │
             structural insight
             
E dado que este grafo se fundamenta na captura de pensamento de um indivíduo especifico que faz uso do on7o em um dispositivo físico, esta arquiterura embora humana é também ego centrica. No entanto, com consentimento e um mecanismo de compartilhamento seletivo, vários nós pessoais podem formar uma rede federada HCIN: HCIN(A) ←→ HCIN(B) ←→ HCIN(C). Sem exigir necessariamente um redes sociais centralizadas que possuem o social graph inteiro de uma sociedade digital.

No que é apresentado aqui, o on7o passa a ser o sensor epistemológico, e o HCIN, o modelo de inteligência relacional construído a partir dele.
            

