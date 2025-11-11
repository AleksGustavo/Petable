<?php
session_start();

if (!isset($_SESSION['logado']) || $_SESSION['logado'] !== true) {
    header("Location: login.php");
    exit();
}

$usuario_logado = htmlspecialchars($_SESSION['usuario']);
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
/* GARANTE QUE O SCROLL FUNCIONE CORRETAMENTE NA JANELA */
html, body {
    height: 100%;
    overflow-x: hidden;
    margin: 0;
    
    /* ---------------------------------------------------- */
    /* MUDANÇA: Fundo Principal (body) com as patinhas */
    /* Cor de fundo Bege Aconchegante (#FAFAF5) */
    background-color: #FAFAF5; 
    
    /* Patinhas Sutil (A mesma usada no login/registro) */
    background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><path fill="%23EFEFEA" d="M 50 20 L 70 30 L 60 50 L 80 60 L 60 70 L 40 60 L 50 80 L 30 70 L 40 50 L 20 60 L 30 30 Z M 50 20 C 45 15, 55 15, 50 20 Z M 35 35 C 30 30, 40 30, 35 35 Z M 65 35 C 60 30, 70 30, 65 35 Z M 35 65 C 30 60, 40 60, 35 65 Z M 65 65 C 60 60, 70 60, 65 65 Z"/></svg>');
    background-size: 80px; 
    background-repeat: repeat;
    opacity: 0.9;
    /* ---------------------------------------------------- */
}

.sidebar {
    min-height: 100vh;
    /* ---------------------------------------------------- */
    /* MUDANÇA: Cor Sólida Marrom Escuro do Login */
    background-color: #3E2723; /* Marrom Profundo */
    /* Removido o background-image (patinhas) da sidebar */
    /* ---------------------------------------------------- */
    
    color: white;
    padding-top: 20px;
    position: sticky; 
    top: 0; 
    z-index: 100;
    height: 100%;
}
    
/* ESTILO PADRÃO MANTIDO (Fundo Escuro -> Texto Branco) */
.nav-link {
    color: rgba(255, 255, 255, 0.75);
    transition: all 0.2s;
}
.nav-link:hover {
    color: white;
    /* Hover leve no fundo escuro */
    background-color: rgba(255, 255, 255, 0.1); 
}
.collapse.show .nav-link {
    padding-left: 2rem;
    font-size: 0.95em;
}
.nav-link.active {
    color: white;
    /* Fundo Ativo mais destacado */
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

/* Estilo Compacto para Formulários Grandes (Mantido) */
.main-compact-card {
    padding: 0.5rem !important;
    /* Opcional: Para cards internos, use um fundo branco para contraste */
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

/* Garante que a coluna main se estenda */
main {
    padding-bottom: 50px; 
}
</style>
</head>
<body>

<div class="container-fluid">
    <div class="row"> 
        
        <nav id="sidebarMenu" class="col-md-3 col-lg-2 d-md-block sidebar collapse">
            <div class="position-sticky pt-3">
                <h6 class="sidebar-heading d-flex justify-content-between align-items-center px-3 text-uppercase">
                    <span>Menu</span>
                </h6>
                <ul class="nav flex-column">
                    <li class="nav-item">
                        <a class="nav-link active item-menu-ajax" data-pagina="home.php" href="#">
                            <i class="fas fa-home me-2"></i> Dashboard
                        </a>
                    </li>
                    
                    <li class="nav-item">
                        <a class="nav-link item-menu-ajax" data-pagina="vendas_pdv.php" href="#">
                            <i class="fas fa-cash-register me-2"></i> PDV (Vendas)
                        </a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link dropdown-toggle" data-bs-toggle="collapse" href="#clientesSubmenu" role="button" aria-expanded="false" aria-controls="clientesSubmenu">
                            <i class="fas fa-users me-2"></i>Clientes
                        </a>
                        <div class="collapse" id="clientesSubmenu">
                            <ul class="nav flex-column ms-3">
                                <li class="nav-item">
                                    <a class="nav-link item-menu-ajax" data-pagina="clientes_cadastro.php" href="#">
                                        <i class="fas fa-user-plus me-2"></i> Cadastrar Cliente
                                    </a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link item-menu-ajax" data-pagina="clientes_listar.php" href="#">
                                        <i class="fas fa-list me-2"></i> Listar Clientes
                                    </a>
                                </li>
                            </ul>
                        </div>
                    </li>
                    
                    <li class="nav-item">
                        <a class="nav-link dropdown-toggle" data-bs-toggle="collapse" href="#produtosSubmenu" role="button" aria-expanded="false" aria-controls="produtosSubmenu">
                            <i class="fas fa-box me-2"></i>Produtos
                        </a>
                        <div class="collapse" id="produtosSubmenu">
                            <ul class="nav flex-column ms-3">
                                <li class="nav-item">
                                    <a class="nav-link item-menu-ajax" data-pagina="produtos_cadastro.php" href="#">
                                        <i class="fas fa-plus me-2"></i> Cadastrar Produto
                                    </a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link item-menu-ajax" data-pagina="produtos_listar.php" href="#">
                                        <i class="fas fa-cubes me-2"></i> Listar Produtos
                                    </a>
                                </li>
                            </ul>
                        </div>
                    </li>
                     
                <li class="nav-item">
    <a class="nav-link dropdown-toggle" data-bs-toggle="collapse" href="#servicosSubmenu" role="button" aria-expanded="false" aria-controls="servicosSubmenu">
        <i class="fas fa-box me-2"></i>Serviços
    </a>
    
    <div class="collapse" id="servicosSubmenu">
        <ul class="nav flex-column ms-3">
            
            <li class="nav-item">
                <a class="nav-link item-menu-ajax" data-pagina="servicos_agendar_banhotosa.php" href="#">
                    <i class="fas fa-calendar-plus me-2"></i> Agendamentos
                </a>
            
            <li class="nav-item">
                <a class="nav-link item-menu-ajax" data-pagina="servicos_agendamentos_listar.php" href="#">
                    <i class="fas fa-list me-2"></i> Listagem Agendamentos
                </a>
            </li>
            
        </ul>
    </div>
</li>
                
                <hr class="my-3 text-white-50">
                
                <ul class="nav flex-column mb-2">
                    <li class="nav-item">
                        <a class="nav-link item-menu-ajax" data-pagina="configuracoes.php" href="#">
                            <i class="fas fa-cog me-2"></i> Configurações
                        </a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" href="logout.php">
                            <i class="fas fa-sign-out-alt me-2"></i> Sair (Logout)
                        </a>
                    </li>
                </ul>
            </div>
        </nav>
        <main class="col-md-9 ms-sm-auto col-lg-10 px-md-4">
            <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
                <h1 class="h2">Painel de Gerenciamento</h1>
                <div class="btn-toolbar mb-2 mb-md-0">
                    <div class="me-2 text-muted">
                        Bem-vindo(a), <strong class="text-primary"><?php echo $usuario_logado; ?></strong>!
                    </div>
                </div>
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
    // FUNÇÃO QUE APLICA AS MÁSCARAS E VALIDAÇÕES (Chamada após cada carregamento AJAX)
    function inicializarMascarasEValidacoes() {
        
        // 1. Aplicação das Máscaras (jQuery Mask Plugin)
        $('.mask-cpf').mask('000.000.000-00');
        
        // Máscara de Telefone/Celular (Admite 8 ou 9 dígitos no meio)
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


        // 2. Restrições de Input (Apenas Números ou Apenas Letras)
        $(document).on('keydown', '.input-numbers-only', function (e) {
            // Permitir backspace, delete, tab, escape, enter, ctrl/meta (Cmd) e setas
            if ($.inArray(e.keyCode, [46, 8, 9, 27, 13, 65]) !== -1 ||
                (e.ctrlKey === true || e.metaKey === true) || 
                (e.keyCode >= 35 && e.keyCode <= 40)) {
                    return;
            }
            // Garante que é um número (teclas 0-9 normais e do numpad)
            if ((e.shiftKey || (e.keyCode < 48 || e.keyCode > 57)) && (e.keyCode < 96 || e.keyCode > 105)) {
                e.preventDefault();
            }
        });

        // Apenas letras e espaço
        $(document).on('keypress', '.input-letters-only', function (e) {
            var charCode = (e.which) ? e.which : e.keyCode;
            // Validação mais estrita para letras, espaço e caracteres latinos (acentos comuns, ç)
            var isValid = (charCode >= 65 && charCode <= 90) || 
                          (charCode >= 97 && charCode <= 122) || 
                          charCode === 32 || 
                          (charCode > 192);
            
            if (!isValid) {
                e.preventDefault();
            }
        });
    }

    
    // FUNÇÃO DE NAVEGAÇÃO AJUSTADA PARA USAR O HISTÓRICO
    function carregarConteudo(pagina, pushHistory = true) {
        // Oculta o conteúdo atual e mostra o loader
        $('#conteudo-dinamico').html('<div class="text-center mt-5"><i class="fas fa-spinner fa-spin fa-3x text-primary"></i><p class="mt-2 text-muted">Carregando...</p></div>');
        
        // Faz a requisição AJAX
        $.ajax({
            url: pagina, 
            type: 'GET',
            success: function(data) {
                // Insere o HTML retornado (o conteúdo da página)
                $('#conteudo-dinamico').html(data);

                // Chama a função de inicialização APÓS o novo HTML ser injetado.
                inicializarMascarasEValidacoes(); 
                
                // ATUALIZA O HISTÓRICO DO NAVEGADOR
                if (pushHistory) {
                    // Refinamento no título para ficar mais legível
                    let tituloBase = 'Gerenciamento';
                    let paginaFormatada = pagina.replace('.php', '').split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
                    let titulo = `${tituloBase} - ${paginaFormatada}`;
                    
                    history.pushState({pagina: pagina}, titulo, `?p=${pagina}`);
                    document.title = titulo; // Atualiza o título
                }
                
                // Atualiza o estado "active" do menu lateral
                $('.nav-link[data-pagina]').removeClass('active');
                $(`.nav-link[data-pagina="${pagina}"]`).addClass('active');

                // Garante que o dropdown correto está expandido (se a página for um subitem)
                const $activeLink = $(`.nav-link[data-pagina="${pagina}"]`);
                const $parentCollapse = $activeLink.closest('.collapse');
                if ($parentCollapse.length) {
                    const collapseInstance = bootstrap.Collapse.getOrCreateInstance($parentCollapse.get(0));
                    collapseInstance.show();
                }
            },
            error: function(xhr, status, error) {
                // Em caso de erro
                $('#conteudo-dinamico').html('<div class="alert alert-danger">Erro ao carregar o conteúdo da página ' + pagina + '. Status: ' + xhr.status + ' (' + status + ')</div>');
                console.error("Erro no AJAX de Navegação:", status, error, xhr.responseText);
            }
        });
    }

    $(document).ready(function() {
        
        const urlParams = new URLSearchParams(window.location.search);
        const paginaInicial = urlParams.get('p') || 'home.php';

        // 1. LÓGICA DE NAVEGAÇÃO POR MENU (DELEGAÇÃO)
        $(document).on('click', '.item-menu-ajax', function(e) {
            e.preventDefault(); 
            
            var $this = $(this);
            var pagina = $this.data('pagina');

            if (pagina) {
                carregarConteudo(pagina);
            }
        });

        // 2. LÓGICA DE ENVIO DE FORMULÁRIOS DE CADASTRO/PROCESSAMENTO
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

        // 3. LÓGICA DE BUSCA RÁPIDA DE CLIENTES
        $(document).on('submit', '#form-busca-cliente-rapida', function(e) {
            e.preventDefault(); 
            
            var form = $(this);
            var dados = form.serialize(); 
            var resultadoArea = $('#resultado-busca-rapida'); 

            resultadoArea.html('<div class="text-center mt-3"><i class="fas fa-spinner fa-spin me-2 text-primary"></i> Buscando...</div>');

            $.ajax({
                type: 'GET', 
                url: 'clientes_buscar_rapido.php', 
                data: dados,
                dataType: 'html', 
                success: function(data) {
                    resultadoArea.html(data); 
                },
                error: function(xhr, status, error) {
                    resultadoArea.html('<div class="alert alert-danger mt-3">Erro ao buscar clientes. Status: ' + xhr.status + '</div>');
                    console.error("Erro no AJAX de Busca:", xhr.responseText);
                }
            });
        });

        // 4. MONITORA O BOTÃO VOLTAR/AVANÇAR (API HISTORY)
        window.onpopstate = function(event) {
            if (event.state && event.state.pagina) {
                carregarConteudo(event.state.pagina, false); 
            } else {
                carregarConteudo(paginaInicial, false);
            }
        };

        // 5. LÓGICA DE LISTAR TODOS OS CLIENTES
        $(document).on('click', '#btn-listar-todos-clientes', function(e) {
            e.preventDefault(); 
            
            var resultadoArea = $('#resultado-busca-rapida'); 

            $('#form-busca-cliente-rapida')[0].reset();
            
            resultadoArea.html('<div class="text-center mt-3"><i class="fas fa-spinner fa-spin me-2 text-info"></i> Carregando todos os clientes...</div>');

            $.ajax({
                type: 'GET', 
                url: 'clientes_buscar_rapido.php', 
                data: { 
                    busca_id: '', 
                    busca_cpf: '', 
                    busca_nome: '',
                    listar_todos: 'true' 
                },
                dataType: 'html', 
                success: function(data) {
                    resultadoArea.html(data); 
                },
                error: function(xhr, status, error) {
                    resultadoArea.html('<div class="alert alert-danger mt-3">Erro ao listar todos os clientes. Status: ' + xhr.status + '</div>');
                    console.error("Erro no AJAX de Listagem Completa:", xhr.responseText);
                }
            });
        });
        
        // 6. LÓGICA DE ESCONDER CLIENTES
        $(document).on('click', '#btn-esconder-clientes', function(e) {
            e.preventDefault(); 
            
            var resultadoArea = $('#resultado-busca-rapida'); 

            resultadoArea.html(`
                <div class="alert alert-info">
                    Preencha um ou mais campos. A busca retornará clientes que correspondam a pelo menos uma das informações fornecidas (ID, CPF ou Nome).
                </div>
            `);
        });

        
        // 7. LÓGICA DE EXCLUSÃO DE CLIENTES (A CORREÇÃO QUE FALTAVA)
        $(document).on('click', '.btn-excluir-cliente', function(e) {
            e.preventDefault(); 
            
            var $this = $(this);
            var id_cliente_para_excluir = $this.data('id'); 

            if (!id_cliente_para_excluir) {
                alert('Erro interno: ID do cliente para exclusão não encontrado.');
                return;
            }

            if (confirm('ATENÇÃO: Você realmente deseja excluir o Cliente ID ' + id_cliente_para_excluir + ' e todos os seus Pets/dados relacionados? Esta ação é IRREVERSÍVEL!')) {
                
                $('#status-message-area').html('<div class="alert alert-warning text-center"><i class="fas fa-spinner fa-spin me-2"></i> Excluindo cliente...</div>');

                $.ajax({
                    url: 'clientes_excluir.php', 
                    type: 'POST', 
                    dataType: 'json',
                    data: { id_cliente: id_cliente_para_excluir }, // Variável esperada pelo PHP
                    
                    success: function(response) {
                        if (response.success) {
                            $('#status-message-area').html('<div class="alert alert-success">' + response.message + '</div>');
                            
                            // Recarrega a lista de clientes para remover o excluído
                            setTimeout(function() { 
                                carregarConteudo('clientes_listar.php'); 
                            }, 1500); 

                        } else {
                            $('#status-message-area').html('<div class="alert alert-danger">Falha na exclusão: ' + response.message + '</div>');
                        }
                    },
                    error: function(xhr, status, error) {
                        // Exibe a resposta crua do PHP para debug
                        $('#status-message-area').html('<div class="alert alert-danger">Erro de Comunicação com o Servidor! Status: ' + xhr.status + ' (' + status + '). Verifique o console.</div>');
                        console.error("ERRO CRÍTICO NA EXCLUSÃO:", xhr.responseText);
                    }
                });
            }
        });
        // LÓGICA DE BUSCA RÁPIDA DE PRODUTOS
// Você já tem a lógica de busca de clientes (seção 3). Adapte para produtos.
$(document).on('submit', '#form-busca-produto-rapida', function(e) {
    e.preventDefault(); // ESSENCIAL: Impede o redirecionamento padrão
    
    var form = $(this);
    var dados = form.serialize(); 
    var resultadoArea = $('#resultado-busca-rapida'); 

    resultadoArea.html('<div class="text-center mt-3"><i class="fas fa-spinner fa-spin me-2 text-primary"></i> Buscando produtos...</div>');

    $.ajax({
        type: 'GET',
        url: 'produtos_buscar_rapido.php', 
        data: dados,
        dataType: 'html', 
        success: function(data) {
            resultadoArea.html(data); 
            inicializarMascarasEValidacoes(); // Reaplicar máscaras se houver campos novos
        },
        error: function(xhr, status, error) {
            resultadoArea.html('<div class="alert alert-danger mt-3">Erro ao buscar produtos.</div>');
        }
    });
});
// LÓGICA DE EXCLUSÃO DE PRODUTO VIA AJAX
$(document).on('click', '.btn-excluir-produto', function(e) {
    e.preventDefault(); // CRUCIAL para evitar qualquer navegação
    e.stopPropagation(); 
    
    // ... (O restante da lógica AJAX para produtos_excluir.php)
    var $button = $(this);
    var idProduto = $button.data('id');
    var $row = $button.closest('tr'); 
    
    if (confirm('Tem certeza que deseja EXCLUIR o produto?')) {
        // ... (Lógica AJAX POST para 'produtos_excluir.php')
        $.ajax({
            type: 'POST',
            url: 'produtos_excluir.php', 
            data: { id_produto: idProduto }, 
            dataType: 'json', 
            // ... (Restante do sucesso e erro)
            success: function(response) {
                if (response.success) {
                    $('#status-message-area').html('<div class="alert alert-success">' + response.message + '</div>');
                    $row.fadeOut(500, function() { $(this).remove(); });
                } else {
                    $('#status-message-area').html('<div class="alert alert-danger">Erro: ' + response.message + '</div>');
                }
            },
            error: function(xhr, status, error) {
                $('#status-message-area').html('<div class="alert alert-danger">Erro de comunicação ao excluir.</div>');
            }
        });
    }
});
// LÓGICA DE LISTAR TODOS OS PRODUTOS
$(document).on('click', '#btn-listar-todos-produtos', function(e) {
    e.preventDefault();
    // Isso deve acionar a busca em produtos_buscar_rapido.php com o parâmetro 'listar_todos'
    
    var resultadoArea = $('#resultado-busca-rapida'); 
    resultadoArea.html('<div class="text-center mt-3"><i class="fas fa-spinner fa-spin me-2 text-info"></i> Carregando todos os produtos...</div>');

    $.ajax({
        type: 'GET',
        url: 'produtos_buscar_rapido.php', 
        data: { 
            busca_nome: '', 
            busca_categoria: '', 
            busca_fornecedor: '', 
            listar_todos: 'true' // Este parâmetro diz ao PHP para ignorar filtros
        },
        dataType: 'html', 
        success: function(data) {
            resultadoArea.html(data);
            // Opcional: Recarrega a página de listagem completa, que já faz a listagem completa por padrão:
            // carregarConteudo('produtos_listar.php'); 
        },
        error: function(xhr, status, error) {
             resultadoArea.html('<div class="alert alert-danger mt-3">Erro ao listar todos os produtos.</div>');
        }
    });
});

// LÓGICA DE ESCONDER/MOSTRAR TODOS OS PRODUTOS (Alternar Visibilidade da Lista)
$(document).on('click', '#btn-esconder-produtos', function(e) {
    e.preventDefault();
    
    var $button = $(this);
    var $containerLista = $('#resultado-busca-rapida'); // O container principal
    
    // Verifica se o container está visível (se ele NÃO tem a classe 'd-none')
    if (!$containerLista.hasClass('d-none')) {
        
        // A lista está visível, vamos ESCONDER
        
        // 1. Esconde o container principal com animação
        $containerLista.fadeOut(300, function() {
            // Adiciona a classe d-none após a animação para garantir o estado oculto
            $(this).addClass('d-none');
        }); 
        
        // 2. Atualiza o botão para "Mostrar Lista"
        $button.removeClass('btn-warning').addClass('btn-secondary'); // Mudar cor/estado
        $button.html('<i class="fas fa-eye me-2"></i> Mostrar Lista');
        
        // Remove qualquer mensagem de status de filtro (opcional)
        $('#status-message-area').html('');
        
    } else {
        
        // A lista está oculta, vamos MOSTRAR
        
        // 1. Remove a classe d-none imediatamente para que o fadeIn funcione
        $containerLista.removeClass('d-none');
        
        // 2. Mostra o container principal com animação
        $containerLista.fadeIn(300);
        
        // 3. Atualiza o botão para "Esconder Lista"
        $button.removeClass('btn-secondary').addClass('btn-warning');
        $button.html('<i class="fas fa-eye-slash me-2"></i> Esconder Lista');
        
    }
  });
        
        // CARREGA O CONTEÚDO INICIAL
        carregarConteudo(paginaInicial, false); 
    });
</script>
</body>
</html>