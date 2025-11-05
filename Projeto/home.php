<?php
// Arquivo: home.php (Visão geral do Painel de Controle)
// Requer o arquivo de conexão. Assumimos que $conexao é uma variável MySQLi válida.
require_once 'conexao.php';

// Inicialização das variáveis para contadores do Dashboard
$total_clientes = 0;
$total_produtos = 0;
$produtos_baixo_estoque = 0;

// Garantimos que $conexao é um objeto de conexão válido antes de tentar usá-lo
if (isset($conexao) && $conexao) {
    try {
        // --- 1. Busca total de Clientes Ativos ---
        $sql_clientes = "SELECT COUNT(id) AS total FROM cliente WHERE ativo = 1";
        $result_clientes = mysqli_query($conexao, $sql_clientes);
        
        if ($result_clientes && $row = mysqli_fetch_assoc($result_clientes)) {
            $total_clientes = $row['total'];
        }
        
        // --- 2. Busca total de Produtos Ativos ---
        $sql_produtos = "SELECT COUNT(id) AS total FROM produto WHERE ativo = 1";
        $result_produtos = mysqli_query($conexao, $sql_produtos);
        
        if ($result_produtos && $row = mysqli_fetch_assoc($result_produtos)) {
            $total_produtos = $row['total'];
        }
        
        // --- 3. Busca de Produtos em Estoque Crítico ---
        $sql_baixo_estoque = "SELECT COUNT(id) AS total FROM produto WHERE quantidade_estoque <= estoque_minimo AND ativo = 1";
        $result_baixo_estoque = mysqli_query($conexao, $sql_baixo_estoque);

        if ($result_baixo_estoque && $row = mysqli_fetch_assoc($result_baixo_estoque)) {
            $produtos_baixo_estoque = $row['total'];
        }

    } catch (Exception $e) {
        // Em caso de erro de conexão ou query, registra o erro e mantém os contadores em 0
        error_log("Erro ao buscar contadores na home: " . $e->getMessage());
    }
} else {
    error_log("Variável \$conexao não está definida ou é inválida na home.php.");
}

// O restante do código é a estrutura HTML/Bootstrap para exibir os cards de atalho
?>

<div class="row mb-4">
    <div class="col-12">
        <h3 class="mb-3">Bem-vindo(a) ao Painel de Controle!</h3>
        <p class="text-muted">Atalhos rápidos para as operações essenciais do dia a dia.</p>
    </div>
</div>

<div class="row">
    
    <!-- CARD 1: Ponto de Venda (PDV) - Verde: Transação e Finalização -->
    <div class="col-lg-4 col-md-6 mb-4">
        <div class="card h-100 shadow-sm border-success">
            <div class="card-body text-center">
                <!-- Ícone de caixa registradora ou carrinho de compras -->
                <i class="fas fa-cash-register fa-3x text-success mb-3"></i>
                <h5 class="card-title">Ponto de Venda (PDV)</h5>
                <p class="card-text text-muted">Inicie uma nova venda de produtos ou serviços agora.</p>
                <a href="#" class="btn btn-success btn-sm item-menu-ajax" data-pagina="vendas_pdv.php">
                    <i class="fas fa-coins me-1"></i> Iniciar Venda
                </a>
            </div>
        </div>
    </div>
    
    <!-- CARD 2: Cadastro de Cliente - Azul: Informação e Cadastro Essencial -->
    <div class="col-lg-4 col-md-6 mb-4">
        <div class="card h-100 shadow-sm border-primary">
            <div class="card-body text-center">
                <!-- Ícone de adicionar usuário -->
                <i class="fas fa-user-plus fa-3x text-primary mb-3"></i>
                <h5 class="card-title">Cadastro de Cliente</h5>
                <p class="card-text text-muted">Adicione novos clientes e seus dados no sistema.</p>
                <a href="#" class="btn btn-primary btn-sm item-menu-ajax" data-pagina="clientes_cadastro.php">
                    <i class="fas fa-address-card me-1"></i> Cadastrar Cliente
                </a>
            </div>
        </div>
    </div>

    <!-- CARD 3: Agendamento - Ciano: Planejamento e Tempo -->
    <div class="col-lg-4 col-md-6 mb-4">
        <div class="card h-100 shadow-sm border-info">
            <div class="card-body text-center">
                <!-- Ícone de adicionar ao calendário -->
                <i class="fas fa-calendar-plus fa-3x text-info mb-3"></i>
                <h5 class="card-title">Novo Agendamento</h5>
                <p class="card-text text-muted">Marque horários para serviços como banho e tosa.</p>
                <a href="#" class="btn btn-info btn-sm item-menu-ajax" data-pagina="servicos_agendar.php">
                    <i class="fas fa-clock me-1"></i> Agendar Serviço
                </a>
            </div>
        </div>
    </div>
    
    <!-- CARD 4: Cadastro de Produto - Roxo/Secundário: Inventário e Itens -->
    <div class="col-lg-4 col-md-6 mb-4">
        <div class="card h-100 shadow-sm border-secondary">
            <div class="card-body text-center">
                <!-- Ícone de etiqueta de preço ou caixa -->
                <i class="fas fa-tags fa-3x text-secondary mb-3"></i>
                <h5 class="card-title">Cadastro de Produto</h5>
                <p class="card-text text-muted">Registre novos itens, preços e informações de estoque.</p>
                <a href="#" class="btn btn-secondary btn-sm item-menu-ajax" data-pagina="produtos_cadastro.php">
                    <i class="fas fa-plus me-1"></i> Cadastrar Produto
                </a>
            </div>
        </div>
    </div>

    <!-- CARD 5: Relatórios e Estatísticas - Amarelo: Análise e Insight -->
    <div class="col-lg-4 col-md-6 mb-4">
        <div class="card h-100 shadow-sm border-warning">
            <div class="card-body text-center">
                <i class="fas fa-chart-bar fa-3x text-warning mb-3"></i>
                <h5 class="card-title">Relatórios e Estatísticas</h5>
                <p class="card-text text-muted">Vendas, Pets mais atendidos, e desempenho geral.</p>
                <a href="#" class="btn btn-warning btn-sm item-menu-ajax" data-pagina="relatorios_listar.php">
                    <i class="fas fa-arrow-right me-1"></i> Visualizar
                </a>
            </div>
        </div>
    </div>
    
    <!-- CARD 6: Estoque Crítico (Alerta) - Vermelho: Urgência e Atenção -->
    <div class="col-lg-4 col-md-6 mb-4">
        <div class="card h-100 shadow-sm border-danger">
            <div class="card-body text-center">
                <!-- Ícone de alerta de perigo -->
                <i class="fas fa-exclamation-triangle fa-3x text-danger mb-3"></i>
                <h5 class="card-title">Estoque Crítico</h5>
                <p class="card-text">
                    <span class="fs-4 text-danger"><?= $produtos_baixo_estoque ?></span> produtos precisam de reposição.
                </p>
                <a href="#" class="btn btn-danger btn-sm item-menu-ajax" data-pagina="produtos_listar.php?filtro=critico">
                    <i class="fas fa-box me-1"></i> Repor/Ver Produtos
                </a>
            </div>
        </div>
    </div>
    
</div>
