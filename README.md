# 🐾 Pet & Pet - Sistema de Gerenciamento para Petshops


**Cliente:** Agro Família (Tatuí / SP)
**Desenvolvimento:** Fatec Araras "Antônio Brambilla" - Projeto Integrador 2º Semestre/2025

---

## 💡 Sobre o Projeto

O **Pet & Pet** é um sistema de **gestão web** desenvolvido para simplificar e automatizar as operações diárias de petshops e clínicas veterinárias. [cite_start]O foco principal é no gerenciamento de **clientes**, **animais**, **agendamentos** e **produtos**, utilizando uma interface intuitiva para operações de CRUD (Create, Read, Update, Delete)[cite: 287, 288, 289].

### Missão
[cite_start]Proporcionar soluções tecnológicas inovadoras que simplifiquem a gestão de petshops, facilitando o dia a dia dos profissionais e melhorando a experiência dos clientes e seus animais[cite: 271].

### Objetivos Principais
* [cite_start]**Centralizar e Organizar** todos os dados do petshop em uma única plataforma[cite: 291].
* [cite_start]**Facilitar** o agendamento e controle de serviços[cite: 291].
* [cite_start]**Gerenciar** o cadastro de clientes e seus pets[cite: 292].
* [cite_start]**Controlar** o estoque de produtos comercializados[cite: 292].

---

## 🛠️ Tecnologias Utilizadas (Stack)

[cite_start]O sistema foi desenvolvido como uma aplicação web seguindo a **Metodologia Incremental** [cite: 381, 412][cite_start], com a gestão do fluxo de trabalho realizada via **Kanban** utilizando a ferramenta **Jira Software**[cite: 384, 387, 411].

| Categoria | Recurso / Ferramenta | Descrição |
| :--- | :--- | :--- |
| **Back-end** | **PHP** | [cite_start]Linguagem principal de desenvolvimento[cite: 363, 402]. |
| **Front-end** | **HTML, CSS, JavaScript** | [cite_start]Linguagens base para o desenvolvimento[cite: 363, 402]. |
| **Framework CSS** | **Bootstrap** | [cite_start]Para agilizar o desenvolvimento da interface e garantir a responsividade[cite: 403]. |
| **Banco de Dados** | **MySQL** | [cite_start]SGBD de código aberto, confiável e com integração nativa ao PHP[cite: 367, 406, 407]. |
| **Modelagem** | **brModelo** | [cite_start]Utilizado para criar os modelos Conceitual, Lógico e Físico do banco de dados[cite: 405]. |
| **Diagramação** | **Plant UML** e **app.diagrams (draw.io)** | [cite_start]Para geração eficiente de Diagramas UML (Sequência, Componentes) e fluxogramas[cite: 408, 409]. |
| **IDE** | **Visual Studio Code (VSCode)** | [cite_start]Editor principal para codificação[cite: 404]. |

---

## 🚀 Requisitos Funcionais (Funcionalidades Implementadas)

[cite_start]O sistema implementa as seguintes funcionalidades essenciais para a gestão de um petshop[cite: 299, 303, 304, 311, 315, 319, 323]:

| Código | Funcionalidade | Descrição | Nível |
| :--- | :--- | :--- | :--- |
| **RF01** | Autenticação de Usuário | [cite_start]Login e Logout com verificação de credenciais[cite: 299]. | Essencial |
| **RF02** | CRUD de Funcionários | [cite_start]Cadastro, consulta, alteração e exclusão de funcionários[cite: 301, 302, 303]. | Essencial |
| **RF03** | CRUD de Clientes | [cite_start]Gestão completa do cadastro de clientes[cite: 304]. | Essencial |
| **RF04** | CRUD de Pets | [cite_start]Gestão do cadastro dos animais, vinculados aos clientes[cite: 309, 310, 311]. | Essencial |
| **RF05** | Criação de Agendamentos | [cite_start]Agendamento de serviços, selecionando pet, serviço, data e funcionário[cite: 313, 314, 315]. | Essencial |
| **RF06** | CRUD de Serviços | [cite_start]Gestão de tipos de serviços oferecidos (preço, descrição)[cite: 317, 318, 319]. | Essencial |
| **RF07** | CRUD de Produtos | [cite_start]Gestão do inventário de produtos (preço de venda, custo, categoria)[cite: 321, 322, 323]. | Essencial |
| **RF09** | Registro de PDV | [cite_start]Registro rápido de vendas (produtos e/ou serviços) com forma de pagamento[cite: 329, 330, 331]. | Importante |

---

## 🔗 Links do Projeto

* **Repositório (Este Link):** `[LINK_DO_REPOSITORIO]`
* [cite_start]**Jira Software (Gerenciamento de Projetos):** [https://petepet.atlassian.net/jira/software/projects/KAN/boards/1](https://petepet.atlassian.net/jira/software/projects/KAN/boards/1) [cite: 281, 394]
* [cite_start]**Documentação de Engenharia de Software:** [https://github.com/AleksGustavo/PI-2--Semestre-2025/tree/mainDocumen-ta%C3%A7%C3%A3o/Engenharia%20de%20Software](https://github.com/AleksGustavo/PI-2--Semestre-2025/tree/mainDocumen-ta%C3%A7%C3%A3o/Engenharia%20de%20Software) [cite: 282]

---

## 👥 Equipe de Desenvolvimento

| Integrante | Papel Principal |
| :--- | :--- |
| **Aleksander Gustavo Assis** | [cite_start]Product Owner e Banco de Dados [cite: 239] |
| **Everton Rodrigues da Silva** | [cite_start]Engenharia de Software [cite: 239] |
| **Marcos Firmino Rodrigues** | [cite_start]Desenvolvedor Back-End [cite: 239] |
| **Wanderson Jaime de A. Santos** | [cite_start]Desenvolvedor Front-End [cite: 239] |

---

## 📈 Modelagem e Arquitetura

Para detalhes da arquitetura e estrutura do sistema, consulte os diagramas:

* **Diagrama de Caso de Uso:** * **Diagrama de Sequência:** * **Diagrama de Classes:** * **Modelo Conceitual/Lógico/Físico do Banco de Dados:** 
---

## 📝 Documentação Detalhada

Para informações completas sobre cronograma, estratégias de testes e garantia de qualidade (QA), consulte as seções correspondentes na documentação do projeto.

* [cite_start]**Estratégia de Testes:** Focada em **Testes de Usabilidade (UX)** com cenários essenciais (Cadastro, Agendamento, Venda)[cite: 424, 428].
* [cite_start]**Garantia de Qualidade:** Inclui **Revisão de Código**, **Testes de Unidade Automatizados**, **Controle de Versão (Git)** e **Homologação**[cite: 439, 440, 441].
