<?php
session_start(); 

// 1. VERIFICAÇÃO DE AUTENTICAÇÃO
if (!isset($_SESSION['usuario_logado']) || $_SESSION['usuario_logado'] !== true) {
    header("Location: login.php");
    exit();
}
?>

<script>
    
    function inicializarMascarasEValidacoes() {
        $('.mask-cpf').mask('000.000.000-00');
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
                $(`.nav-link[data-pagina="${pagina}"]`).addClass('active');

                const $activeLink = $(`.nav-link[data-pagina="${pagina}"]`);
                const $parentCollapse = $activeLink.closest('.collapse');
                if ($parentCollapse.length) {
                    const collapseInstance = bootstrap.Collapse.getOrCreateInstance($parentCollapse.get(0));
                    collapseInstance.show();
                }
            },
            error: function(xhr, status, error) {
                $('#conteudo-dinamico').html('<div class="alert alert-danger">Erro ao carregar o conteúdo da página ' + pagina + '. Status: ' + xhr.status + ' (' + status + ')</div>');
                console.error("Erro no AJAX de Navegação:", status, error, xhr.responseText);
            }
        });
    }

    // NOVA FUNÇÃO: LÓGICA CENTRALIZADA DE CARREGAMENTO DE PÁGINAS DE CLIENTES
    function carregarPaginaClientes(pagina = 1, termoBusca = {}, listarTodos = false) {
        const limite = 10; 
        const offset = (pagina - 1) * limite;
        
        var resultadoArea = $('#resultado-busca-rapida'); 

        resultadoArea.html('<div class="text-center mt-3"><i class="fas fa-spinner fa-spin me-2 text-info"></i> Carregando resultados...</div>');

        // Prepara os dados a serem enviados
        let dados = { 
            pagina_atual: pagina, 
            limite: limite, 
            offset: offset, 
            listar_todos: listarTodos ? 'true' : 'false',
            // Adiciona termos de busca se existirem
            ...termoBusca 
        };

        $.ajax({
            type: 'GET', 
            url: 'clientes_buscar_rapido.php', 
            data: dados,
            dataType: 'html', 
            success: function(data) {
                resultadoArea.html(data); 
            },
            error: function(xhr, status, error) {
                resultadoArea.html('<div class="alert alert-danger mt-3">Erro ao carregar a página de clientes. Status: ' + xhr.status + '</div>');
                console.error("Erro no AJAX de Paginação:", xhr.responseText);
            }
        });
    }

    $(document).ready(function() {
        
        // LÓGICA CORRETA PARA DEFINIR A PÁGINA INICIAL
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

        // LÓGICA DE BUSCA RÁPIDA DE CLIENTES (MODIFICADA para chamar a nova função)
        $(document).on('submit', '#form-busca-cliente-rapida', function(e) {
            e.preventDefault(); 
            
            var form = $(this);
            var dados = form.serializeArray().reduce(function(obj, item) {
                obj[item.name] = item.value;
                return obj;
            }, {});

            delete dados['listar_todos']; 

            // Chama a função de carregamento da página 1 com os termos de busca
            carregarPaginaClientes(1, dados, false);
        });


        // MONITORA O BOTÃO VOLTAR/AVANÇAR
        window.onpopstate = function(event) {
            if (event.state && event.state.pagina) {
                carregarConteudo(event.state.pagina, false); 
            } else {
                carregarConteudo(paginaInicial, false);
            }
        };

        // LÓGICA DE LISTAR TODOS OS CLIENTES (MODIFICADA para chamar a página 1)
        $(document).on('click', '#btn-listar-todos-clientes', function(e) {
            e.preventDefault(); 
            
            $('#form-busca-cliente-rapida')[0].reset();
            
            carregarPaginaClientes(1, {}, true); 
        });

        // NOVA LÓGICA: LIDA COM OS BOTÕES DE PAGINAÇÃO (1, 2, 3... e Próximos 10)
        $(document).on('click', '.btn-pagina-cliente', function(e) {
            e.preventDefault(); 
            
            var paginaDesejada = $(this).data('pagina');
            var listarTodos = $(this).data('listar-todos') === true; 
            
            var termoBusca = {};
            if (!listarTodos) {
                 termoBusca = $('#form-busca-cliente-rapida').serializeArray().reduce(function(obj, item) {
                     obj[item.name] = item.value;
                     return obj;
                 }, {});
            }

            carregarPaginaClientes(paginaDesejada, termoBusca, listarTodos);
        });
        
        // LÓGICA DE ESCONDER CLIENTES
        $(document).on('click', '#btn-esconder-clientes', function(e) {
            e.preventDefault(); 
            var resultadoArea = $('#resultado-busca-rapida'); 
            resultadoArea.html(`
                <div class="alert alert-info">
                    Preencha um ou mais campos. A busca retornará clientes que correspondam a pelo menos uma das informações fornecidas (ID, CPF ou Nome).
                </div>
            `);
        });

        // CARREGA O CONTEÚDO INICIAL (home.php ou o que estiver em ?p=...)
        carregarConteudo(paginaInicial, false); 
    });
</script>