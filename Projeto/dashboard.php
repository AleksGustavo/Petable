<?php
session_start();

if (!isset($_SESSION['logado']) || $_SESSION['logado'] !== true) {
    header("Location: login.php");
    exit();
}

$usuario_logado = htmlspecialchars($_SESSION['usuario']);

$pagina_atual = $_GET['p'] ?? 'home.php'; 
?>

<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Gerenciamento - Dashboard</title>
    
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH" crossorigin="anonymous">
    <link rel="stylesheet" href="https://code.jquery.com/ui/1.13.3/themes/base/jquery-ui.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
    
<style>
html, body {
    height: 100%;
    overflow-x: hidden;
    margin: 0;
    
    background-color: #FAFAF5; 
    /* Manter a imagem de fundo */
    background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><path fill="%23EFEFEA" d="M 50 20 L 70 30 L 60 50 L 80 60 L 60 70 L 40 60 L 50 80 L 30 70 L 40 50 L 20 60 L 30 30 Z M 50 20 C 45 15, 55 15, 50 20 Z M 35 35 C 30 30, 40 30, 35 35 Z M 65 35 C 60 30, 70 30, 65 35 Z M 35 65 C 30 60, 40 60, 35 65 Z M 65 65 C 60 60, 70 60, 65 65 Z"/></svg>');
    background-size: 80px; 
    background-repeat: repeat;
    opacity: 0.9;
}

.sidebar {
    /* Corrigir para 'position: fixed;' e 'left: 0;' para fixar a barra lateral e permitir o scroll no main */
    position: fixed; 
    top: 0; 
    bottom: 0;
    left: 0;
    z-index: 100;
    background-color: #3E2723; 
    color: white;
    padding-top: 20px;
    height: 100vh; /* Ocupa a altura total da viewport */
    overflow-y: auto; /* Permite scroll se o menu for muito longo */
}
    
.nav-link {
    color: rgba(255, 255, 255, 0.75);
    transition: all 0.2s;
}
.nav-link:hover {
    color: white;
    background-color: rgba(255, 255, 255, 0.1); 
}
.collapse.show .nav-link {
    padding-left: 2rem;
    font-size: 0.95em;
}
.nav-link.active {
    color: white;
    background-color: rgba(255, 255, 255, 0.2);
    border-radius: 5px;
}
.sidebar-heading {
    margin-top: 0.5rem !important;
    margin-bottom: 0.5rem !important;
    color: #ffffff !important;
    font-size: 1.1rem;
    font-weight: 700;
    letter-spacing: .15rem;
    padding-top: 0 !important;
}

.main-compact-card {
    padding: 0.5rem !important;
    background-color: #fff;
    border-radius: 8px;
    box-shadow: 0 2px 5px rgba(0,0,0,0.1);
}

.g-compact {
    --bs-gutter-y: 0.5rem; 
    --bs-gutter-x: 1.5rem; 
}

.g-compact .form-label {
    margin-bottom: 0.2rem;
    font-size: 0.9rem;
}

.g-compact hr {
    margin-top: 0.75rem !important;
    margin-bottom: 0.75rem !important;
}

main {
    padding-bottom: 50px; 
}

/* Estilos específicos de vendas_detalhes.php para consistência */
:root {
    --bs-primary: #0d6efd;
    --bs-secondary: #6c757d;
    --bs-success: #198754;
    --bs-warning: #ffc107;
    --bs-danger: #dc3545;
}

/* Estilo do Card Principal (semelhante ao card-cliente) */
.card-venda-info { 
    border-left: 5px solid var(--bs-primary); 
    background-color: #f8f9fa; 
}

/* Estilos dos cabeçalhos dos cards */
.card-header-primary {
    background-color: var(--bs-primary) !important;
    color: white !important;
    font-weight: bold;
}
.card-header-secondary {
    background-color: var(--bs-secondary) !important;
    color: white !important;
    font-weight: bold;
}

/* Estilização da Tabela de Itens */
.table-itens { 
    font-size: 0.9rem; 
    --bs-table-hover-bg: #f3f4f6;
}

/* Destaque para o valor total */
.total-box {
    background-color: var(--bs-success);
    color: white;
    padding: 10px 15px;
    border-radius: 5px;
    font-size: 1.25rem;
    font-weight: bold;
    text-align: right;
    box-shadow: 0 4px 6px rgba(0,0,0,0.1);
}
    
.badge-tipo {
    font-size: 0.75rem;
    padding: 0.4em 0.6em;
    border-radius: 0.25rem;
}
/* Fim dos Estilos Específicos */

</style>
</head>
<body>

<div class="container-fluid">
    <div class="row"> 
        
        <nav id="sidebarMenu" class="col-md-3 col-lg-2 d-md-block sidebar">
            <div class="position-sticky pt-3">
                <h6 class="sidebar-heading d-flex justify-content-between align-items-center px-3 text-uppercase">
                    <span>Menu</span>
                </h6>
                <ul class="nav flex-column">
                    <li class="nav-item">
                        <a class="nav-link item-menu-ajax <?php if ($pagina_atual == 'home.php') echo 'active'; ?>" data-pagina="home.php" href="#">
                            <i class="fas fa-home me-2"></i> Dashboard
                        </a>
                    </li>
                    
                    <li class="nav-item">
                        <a class="nav-link item-menu-ajax <?php if ($pagina_atual == 'vendas_pdv.php') echo 'active'; ?>" data-pagina="vendas_pdv.php" href="#">
                            <i class="fas fa-cash-register me-2"></i> PDV (Vendas)
                        </a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link dropdown-toggle <?php if (strpos($pagina_atual, 'clientes_') !== false) echo 'active'; ?>" data-bs-toggle="collapse" href="#clientesSubmenu" role="button" aria-expanded="<?php echo (strpos($pagina_atual, 'clientes_') !== false) ? 'true' : 'false'; ?>" aria-controls="clientesSubmenu">
                            <i class="fas fa-users me-2"></i>Clientes
                        </a>
                        <div class="collapse <?php if (strpos($pagina_atual, 'clientes_') !== false) echo 'show'; ?>" id="clientesSubmenu">
                            <ul class="nav flex-column ms-3">
                                <li class="nav-item">
                                    <a class="nav-link item-menu-ajax <?php if ($pagina_atual == 'clientes_cadastro.php') echo 'active'; ?>" data-pagina="clientes_cadastro.php" href="#">
                                        <i class="fas fa-user-plus me-2"></i> Cadastrar Cliente
                                    </a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link item-menu-ajax <?php if ($pagina_atual == 'clientes_listar.php') echo 'active'; ?>" data-pagina="clientes_listar.php" href="#">
                                        <i class="fas fa-list me-2"></i> Listar Clientes
                                    </a>
                                </li>
                            </ul>
                        </div>
                    </li>
                    
                    <li class="nav-item">
                        <a class="nav-link dropdown-toggle <?php if (strpos($pagina_atual, 'produtos_') !== false) echo 'active'; ?>" data-bs-toggle="collapse" href="#produtosSubmenu" role="button" aria-expanded="<?php echo (strpos($pagina_atual, 'produtos_') !== false) ? 'true' : 'false'; ?>" aria-controls="produtosSubmenu">
                            <i class="fas fa-box me-2"></i>Produtos
                        </a>
                        <div class="collapse <?php if (strpos($pagina_atual, 'produtos_') !== false) echo 'show'; ?>" id="produtosSubmenu">
                            <ul class="nav flex-column ms-3">
                                <li class="nav-item">
                                    <a class="nav-link item-menu-ajax <?php if ($pagina_atual == 'produtos_cadastro.php') echo 'active'; ?>" data-pagina="produtos_cadastro.php" href="#">
                                        <i class="fas fa-plus me-2"></i> Cadastrar Produto
                                    </a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link item-menu-ajax <?php if ($pagina_atual == 'produtos_listar.php') echo 'active'; ?>" data-pagina="produtos_listar.php" href="#">
                                        <i class="fas fa-cubes me-2"></i> Listar Produtos
                                    </a>
                                </li>
                            </ul>
                        </div>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link dropdown-toggle <?php if (strpos($pagina_atual, 'servicos_') !== false) echo 'active'; ?>" data-bs-toggle="collapse" href="#servicosSubmenu" role="button" aria-expanded="<?php echo (strpos($pagina_atual, 'servicos_') !== false) ? 'true' : 'false'; ?>" aria-controls="servicosSubmenu">
                            <i class="fas fa-concierge-bell me-2"></i>Serviços
                        </a>
                        
                        <div class="collapse <?php if (strpos($pagina_atual, 'servicos_') !== false) echo 'show'; ?>" id="servicosSubmenu">
                            <ul class="nav flex-column ms-3">
                                
                                <li class="nav-item">
                                    <a class="nav-link item-menu-ajax <?php if ($pagina_atual == 'servicos_agendar_banhotosa.php') echo 'active'; ?>" data-pagina="servicos_agendar_banhotosa.php" href="#">
                                        <i class="fas fa-calendar-plus me-2"></i> Agendamentos
                                    </a>
                                </li>
                                
                                <li class="nav-item">
                                    <a class="nav-link item-menu-ajax <?php if ($pagina_atual == 'servicos_agendamentos_listar.php') echo 'active'; ?>" data-pagina="servicos_agendamentos_listar.php" href="#">
                                        <i class="fas fa-list me-2"></i> Listagem Agendamentos
                                    </a>
                                </li>
                                
                                <li class="nav-item">
                                    <a class="nav-link item-menu-ajax <?php if ($pagina_atual == 'servicos_listar.php') echo 'active'; ?>" data-pagina="servicos_listar.php" href="#">
                                        <i class="fas fa-cut me-2"></i> Catálogo de Serviços
                                    </a>
                                </li>
                                
                            </ul>
                        </div>
                    </li>
                    <hr class="my-3 text-white-50">
                    
                    <ul class="nav flex-column mb-2">
                        <li class="nav-item">
                            <a class="nav-link item-menu-ajax <?php if ($pagina_atual == 'configuracoes.php') echo 'active'; ?>" data-pagina="configuracoes.php" href="#">
                                <i class="fas fa-cog me-2"></i> Configurações
                            </a>
                        </li>
                        <li class="nav-item">
                            <a class="nav-link" href="logout.php">
                                <i class="fas fa-sign-out-alt me-2"></i> Sair (Logout)
                            </a>
                        </li>
                    </ul>
                </ul>
            </div>
        </nav>
        
        <main class="col-md-9 ms-md-auto col-lg-10 px-md-4">
            <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
                <h1 class="h2">Painel de Gerenciamento</h1>
                <div class="btn-toolbar mb-2 mb-md-0">
                    <div class="me-2 text-muted">
                        Bem-vindo(a), <strong class="text-primary"><?php echo $usuario_logado; ?></strong>!
                    </div>
                </div>
            </div>

            <div id="status-message-area">
                </div>
            
            <div id="conteudo-dinamico">
            </div>

        </main>
    </div>
</div>

<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<script src="https://code.jquery.com/ui/1.13.3/jquery-ui.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/jquery.mask/1.14.16/jquery.mask.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js" integrity="sha384-YvpcrYf0tY3lHB60NNkmXc5s9fDVZLESaAA55NDzOxhy9GkcIdslK1eN7N6jIeHz" crossorigin="anonymous"></script>

<script>
    function inicializarMascarasEValidacoes() {
        
        
        $('.mask-cpf').mask('000.000.000-00');
        
        
        var MaskBehavior = function (val) {
            return val.replace(/\D/g, '').length === 11 ? '(00) 00000-0000' : '(00) 0000-00009';
        },
        options = {
            onKeyPress: function(val, e, field, options) {
                field.mask(MaskBehavior.apply({}, arguments), options);
            }
        };
        $('.mask-celular').mask(MaskBehavior, options);

        $('.mask-cep').mask('00000-000');


        
        
        $(document).on('keydown', '.input-numbers-only', function (e) {
            
            if ($.inArray(e.keyCode, [46, 8, 9, 27, 13, 65]) !== -1 ||
                (e.ctrlKey === true || e.metaKey === true) || 
                (e.keyCode >= 35 && e.keyCode <= 40)) {
                    return;
            }
            
            if ((e.shiftKey || (e.keyCode < 48 || e.keyCode > 57)) && (e.keyCode < 96 || e.keyCode > 105)) {
                e.preventDefault();
            }
        });

        
        $(document).on('keypress', '.input-letters-only', function (e) {
            var charCode = (e.which) ? e.which : e.keyCode;
            
            var isValid = (charCode >= 65 && charCode <= 90) || 
                                       (charCode >= 97 && charCode <= 122) || 
                                       charCode === 32 || 
                                       (charCode > 192);
            
            if (!isValid) {
                e.preventDefault();
            }
        });
    }

    
    function carregarConteudo(pagina, pushHistory = true) {
        
        $('#conteudo-dinamico').html('<div class="text-center mt-5"><i class="fas fa-spinner fa-spin fa-3x text-primary"></i><p class="mt-2 text-muted">Carregando...</p></div>');
        
        
        $.ajax({
            url: pagina, 
            type: 'GET',
            success: function(data) {
                
                $('#conteudo-dinamico').html(data);

                
                inicializarMascarasEValidacoes(); 
                
                
                if (pushHistory) {
                    
                    let tituloBase = 'Gerenciamento';
                    let paginaFormatada = pagina.replace('.php', '').split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
                    let titulo = `${tituloBase} - ${paginaFormatada}`;
                    
                    history.pushState({pagina: pagina}, titulo, `?p=${pagina}`);
                    document.title = titulo; 
                }
                
                
                $('.nav-link[data-pagina]').removeClass('active');
                
                // Ativa o link direto
                $(`.nav-link[data-pagina="${pagina}"]`).addClass('active');

                // Garante que o menu pai esteja ativo e aberto (no caso de submenus)
                const $activeLink = $(`.nav-link[data-pagina="${pagina}"]`);
                const $parentCollapse = $activeLink.closest('.collapse');
                if ($parentCollapse.length) {
                    const collapseInstance = bootstrap.Collapse.getOrCreateInstance($parentCollapse.get(0));
                    collapseInstance.show();
                    // Ativa o link pai (dropdown-toggle)
                    $parentCollapse.prev('.dropdown-toggle').addClass('active');
                } else {
                    // Desativa outros dropdown-toggle se não for um submenu
                    $('.dropdown-toggle').removeClass('active');
                }
            },
            error: function(xhr, status, error) {
                
                $('#conteudo-dinamico').html('<div class="alert alert-danger">Erro ao carregar o conteúdo da página ' + pagina + '. Status: ' + xhr.status + ' (' + status + ')</div>');
                console.error("Erro no AJAX de Navegação:", status, error, xhr.responseText);
            }
        });
    }

    $(document).ready(function() {
        
        const urlParams = new URLSearchParams(window.location.search);
        
        const paginaInicial = urlParams.get('p') || 'home.php'; 

        
        $(document).on('click', '.item-menu-ajax', function(e) {
            e.preventDefault(); 
            
            var $this = $(this);
            var pagina = $this.data('pagina');

            if (pagina) {
                carregarConteudo(pagina);
            }
        });

        
        $(document).on('submit', '#form-cadastro-cliente, #form-cadastro-produto, #form-cadastro-pet, #form-agendar-servico, #form-registrar-vacina, #form-pdv', function(e) {
            e.preventDefault(); 
            
            var form = $(this);
            var url = form.attr('action');
            var dados = form.serialize();
            var formId = form.attr('id');
            
            var paginaRedirecionamento = 'home.php';
            if (formId === 'form-cadastro-cliente' || formId === 'form-cadastro-pet') {
                paginaRedirecionamento = 'clientes_listar.php';
            } else if (formId === 'form-cadastro-produto') {
                paginaRedirecionamento = 'produtos_listar.php';
            } else if (formId === 'form-agendar-servico' || formId === 'form-registrar-vacina') {
                paginaRedirecionamento = 'servicos_agendamentos_listar.php'; 
            } else if (formId === 'form-pdv') {
                paginaRedirecionamento = 'vendas_pdv.php'; 
            }
            
            $('#status-message-area').html('<div class="alert alert-info text-center"><i class="fas fa-spinner fa-spin me-2"></i> Enviando dados...</div>');

            $.ajax({
                type: 'POST',
                url: url,
                data: dados,
                dataType: 'json', 
                success: function(response) {
                    
                    if (response.success === true) { 
                        $('#status-message-area').html('<div class="alert alert-success">' + response.message + '</div>');
                        
                        if (formId !== 'form-pdv') {
                            form[0].reset();
                        }
                        
                        setTimeout(function() { 
                            carregarConteudo(paginaRedirecionamento); 
                        }, 2000); 

                    } else {
                        $('#status-message-area').html('<div class="alert alert-danger">' + response.message + '</div>');
                    }
                },
                error: function(xhr, status, error) {
                    $('#status-message-area').html('<div class="alert alert-danger">Erro de comunicação ou resposta inválida. Status: ' + xhr.status + '</div>');
                    console.error("Erro no AJAX de Envio:", xhr.responseText);
                }
            });
        });

        
        $(document).on('submit', '#form-busca-cliente-rapida', function(e) {
            e.preventDefault(); 
            
        });

        
        window.onpopstate = function(event) {
            if (event.state && event.state.pagina) {
                carregarConteudo(event.state.pagina, false); 
            } else {
                carregarConteudo(paginaInicial, false);
            }
        };

        
        $(document).on('click', '#btn-listar-todos-clientes', function(e) {
            e.preventDefault(); 
            
        });
        
        
        $(document).on('click', '#btn-esconder-clientes', function(e) {
            e.preventDefault(); 
            
        });

        
        $(document).on('click', '.btn-excluir-cliente', function(e) {
            e.preventDefault(); 
            
        });
        
        
        $(document).on('submit', '#form-busca-produto-rapida', function(e) {
            e.preventDefault(); 
            
        });
        
        
        $(document).on('click', '.btn-excluir-produto', function(e) {
            e.preventDefault(); 
            e.stopPropagation(); 
            
        });

        
        $(document).on('click', '#btn-listar-todos-produtos', function(e) {
            e.preventDefault();
            
        });

        
        $(document).on('click', '#btn-esconder-produtos', function(e) {
            e.preventDefault();
            
        });
        
        
        
        
        
        
        
        
        
        
        
        $(document).on('submit', '#filter-form', function(e) {
            e.preventDefault(); 
            
            var form = $(this);
            var dados = form.serialize();
            const agendamentosContentArea = $('#agendamentos-content'); 

            agendamentosContentArea.html('<div class="text-center mt-4"><i class="fas fa-spinner fa-spin fa-2x text-primary"></i><p class="mt-2 text-muted">Aplicando filtro...</p></div>');

            $.ajax({
                type: 'GET',
                
                url: 'servicos_agendamentos_listar.php', 
                data: dados,
                dataType: 'html', 
                success: function(data) {
                    
                    agendamentosContentArea.html(data);
                },
                error: function(xhr) {
                    agendamentosContentArea.html('<div class="alert alert-danger mt-4">Erro ao filtrar agendamentos. Status: ' + xhr.status + '</div>');
                }
            });
        });


        
        $(document).on('click', '.btn-processar-agendamento', function(e) {
            e.preventDefault(); 
            
            var $this = $(this);
            var id = $this.data('id'); 
            var acao = $this.data('acao'); 
            var confirmMessage = '';
            
            
            if (acao === 'deletar') {
                 confirmMessage = 'Tem certeza que deseja DELETAR (excluir permanentemente) o Agendamento ID ' + id + '?';
            } else if (acao === 'finalizar') {
                 confirmMessage = 'Confirma a FINALIZAÇÃO do Agendamento ID ' + id + '?';
            } else if (acao === 'cancelar') {
                 confirmMessage = 'Tem certeza que deseja CANCELAR o Agendamento ID ' + id + '?';
            } else {
                return; 
            }
            
            
            if (confirm(confirmMessage)) {
                $('#status-message-area').html('<div class="alert alert-info text-center"><i class="fas fa-spinner fa-spin me-2"></i> Processando ' + acao + '...</div>');

                $.ajax({
                    url: 'agendamento_processar.php', 
                    type: 'POST',
                    dataType: 'json',
                    data: { id: id, acao: acao }, 
                    success: function(response) {
                        if (response.success) {
                            $('#status-message-area').html('<div class="alert alert-success">' + response.message + '</div>');
                            
                            
                            
                            setTimeout(function() { 
                                 carregarConteudo('servicos_agendamentos_listar.php'); 
                            }, 1000); 
                        } else {
                            $('#status-message-area').html('<div class="alert alert-danger">Falha: ' + response.message + '</div>');
                        }
                    },
                    error: function(xhr) {
                        $('#status-message-area').html('<div class="alert alert-danger">Erro de comunicação ao tentar processar o agendamento.</div>');
                    }
                });
            }
        });
        
        
        $(document).on('click', '#toggle-concluidos', function() {
            
            const $rowsConcluidas = $('#agendamentos-content').find('tr.status-concluido');
            const $button = $(this);
            
            if ($rowsConcluidas.is(':visible')) {
                $rowsConcluidas.hide();
                $button.html('<i class="fas fa-eye me-1"></i> Mostrar Concluídos');
            } else {
                $rowsConcluidas.show();
                $button.html('<i class="fas fa-eye-slash me-1"></i> Ocultar Concluídos');
            }
        });


        // CORREÇÃO: Chama a função para carregar o conteúdo inicial após o DOM estar pronto e os eventos configurados
        carregarConteudo(paginaInicial, false); 
    });
</script>
</body>
</html>