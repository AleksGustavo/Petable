# 🐾 Pet & Pet - Sistema de Gerenciamento para Petshops



**Cliente:** Agro Família (Tatuí / SP)

**Desenvolvimento:** Fatec Araras "Antônio Brambilla" - Projeto Integrador 2º Semestre/2025

---

## 💡 Sobre o Projeto

O **Pet & Pet** é um sistema de **gestão web** desenvolvido em PHP com banco de dados MySQL, projetado para simplificar e automatizar as operações diárias de petshops e clínicas veterinárias. O foco principal é no gerenciamento de **clientes**, **animais**, **agendamentos** e **produtos**, através de um CRUD (Create, Read, Update, Delete) simples e intuitivo.

### Missão
Nossa missão é proporcionar soluções tecnológicas inovadoras que simplifiquem a gestão de petshops, facilitando o dia a dia dos profissionais e melhorando a experiência dos clientes e seus animais.

### Objetivos Principais
* Centralizar e organizar os dados do petshop em uma única plataforma.
* Facilitar o agendamento e controle de serviços.
* Gerenciar o cadastro de clientes e seus respectivos animais.
* Controlar o estoque de produtos comercializados.
* Gerar relatório básico para apoio à decisão gerencial.

---

## 🛠️ Tecnologias e Metodologia

| Categoria | Recurso / Ferramenta | Descrição e Justificativa |
| :--- | :--- | :--- |
| **Linguagens** | HTML, CSS, JavaScript, PHP | Linguagens base para o desenvolvimento front-end e back-end do sistema. |
| **Framework CSS** | Bootstrap | Framework escolhido para acelerar o desenvolvimento da interface, garantir a responsividade e aderir aos padrões de design atuais. |
| **Banco de Dados** | MySQL | Selecionado por sua confiabilidade, alto desempenho e por ser uma solução open source, alinhando-se perfeitamente com a tecnologia PHP. |
| **Modelagem** | brModelo | Ferramenta utilizada para a criação dos Modelos Conceitual, Lógico e Físico do banco de dados. |
| **Gerenciamento** | Jira Software (Kanban) | Plataforma utilizada para administração e rastreamento das tarefas do projeto, configurado com um board Kanban para visualização do fluxo de trabalho. |
| **Metodologia** | Metodologia Incremental | A abordagem adotada, que permite a entrega de partes funcionais do sistema em ciclos curtos, validando o produto com o usuário ao final de cada incremento. |

---

## 🚀 Requisitos Funcionais e Funcionalidades Implementadas

As funcionalidades principais do sistema são:

* **Autenticação de Usuário (RF01):** O sistema deve permitir que o usuário faça login e logout.
* **CRUD de Funcionários (RF02):** Permitir cadastro, consulta, alteração e exclusão de funcionários.
* **CRUD de Clientes (RF03):** Gerenciamento completo de clientes (nome, contato e endereço).
* **CRUD de Pets (RF04):** Gerenciamento dos animais, com vinculação obrigatória a um cliente, espécie e raça.
* **Criação de Agendamentos (RF05):** Agendar serviços, selecionando o pet, o serviço, a data e o funcionário designado.
* **CRUD de Serviços (RF06):** Gerenciar serviços (nome, descrição e preço de padrão).
* **CRUD de Produtos (RF07):** Gerenciar produtos (preço de venda, custo e vinculação à categoria).
* **Registro de PDV (RF09):** Registro rápido de vendas, incluindo produtos e/ou serviços, cliente (opcional) e forma de pagamento.

---

## 🛡️ Qualidade e Testes

A Garantia da Qualidade (QA) foi estabelecida com foco em:

* **Estratégia de Testes:** Principalmente **Teste de Usabilidade (UX)**, com cenários focados na rotina do petshop (Cadastro de Cliente/Pet, Agendamento, Venda).
* **Práticas de QA:** Revisão de Código (**Code Review**), **Testes de Unidade Automatizados** e **Controle de Versão (Git)** para rastreabilidade e reversão.
* **Homologação:** Execução de testes de ponta a ponta em um ambiente idêntico ao de produção antes da liberação final.

### Requisitos Mínimos (Lado do Cliente)
* **Processador:** CPU Dual Core de 2.0 GHz ou superior.
* **Memória RAM:** Mínimo de 4 GB de RAM.
* **Navegador Web:** Uma das últimas três versões do Google Chrome, Mozilla Firefox ou Safari.
* **Conexão com a Internet:** Velocidade mínima de 5 Mbps para acesso estável.

---

## 🔗 Links e Documentação

* **Link Repositório (Completo):** https://github.com/AleksGustavo/PI-2--Semestre-2025
* **Jira Software (Kanban Board):** https://petepet.atlassian.net/jira/software/projects/KAN/boards/1
* **Diário de Bordo (Aleksander):** https://github.com/AleksGustavo/PI-2--Semestre-2025/blob/main/Documenta%C3%A7%C3%A3o/Diario%20de%20Bordo%20-%20Aleksander.md

---

## 👥 Equipe de Desenvolvimento - Grupo 5

| Integrante | Papel Principal |
| :--- | :--- |
| **Aleksander Gustavo Assis** | Product Owner e Banco de Dados |
| **Everton Rodrigues da Silva** | Engenharia de Software |
| **Marcos Firmino Rodrigues** | Desenvolvedor Back-End |
| **Wanderson Jaime de A. Santos** | Desenvolvedor Front-End |
