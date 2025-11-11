<?php
// Arquivo: clientes_cadastro.php
// Apenas o conteúdo HTML que será injetado pelo AJAX.
?>

<div class="d-flex justify-content-between align-items-center mb-3">
    <h2><i class="fas fa-user-plus me-2"></i> Cadastrar Novo Cliente</h2>
    
    <div>
        <a href="#" class="btn btn-secondary item-menu-ajax btn-sm" data-pagina="clientes_listar.php">
            <i class="fas fa-list me-2"></i> Voltar à Lista
        </a>
    </div>
</div>

<div id="status-message-area">
</div>

<div class="card p-0 shadow-sm main-compact-card">
    <div class="card-body">
        <form id="form-cadastro-cliente" method="POST" action="clientes_processar.php">
            <div class="row g-2 g-compact">
                
                <div class="col-md-4">
                    <label for="nome" class="form-label">Nome *</label>
                    <input type="text" id="nome" name="nome" class="form-control form-control-sm input-letters-only" required>
                </div>
                
                <div class="col-md-4">
                    <label for="sobrenome" class="form-label">Sobrenome *</label>
                    <input type="text" id="sobrenome" name="sobrenome" class="form-control form-control-sm input-letters-only" required>
                </div>
                
                <div class="col-md-4">
                    <label for="cpf" class="form-label">CPF *</label>
                    <input type="text" id="cpf" name="cpf" class="form-control form-control-sm mask-cpf input-numbers-only" required maxlength="14" placeholder="000.000.000-00">
                    <div id="cpf-feedback" class="invalid-feedback">
                        CPF inválido.
                    </div>
                </div>
                
                <div class="col-md-4">
                    <label for="celular" class="form-label">Celular *</label>
                    <input type="text" id="celular" name="celular" class="form-control form-control-sm mask-celular input-numbers-only" required maxlength="15" placeholder="(00) 00000-0000">
                </div>

                <hr class="mt-2">
                <h5 class="mb-2">Endereço</h5>

                <div class="col-md-3">
                    <label for="cep" class="form-label">CEP *</label>
                    <input type="text" id="cep" name="cep" class="form-control form-control-sm mask-cep input-numbers-only" required maxlength="9" placeholder="00000-000">
                    <div id="cep-feedback" class="invalid-feedback">
                        CEP não encontrado.
                    </div>
                </div>
                
                <div class="col-md-2">
                    <label for="uf" class="form-label">Estado (UF) *</label>
                    <input type="text" id="uf" name="uf" class="form-control form-control-sm" required maxlength="2" readonly>
                </div>

                <div class="col-md-4">
                    <label for="rua" class="form-label">Rua *</label>
                    <input type="text" id="rua" name="rua" class="form-control form-control-sm" required>
                </div>
                
                <div class="col-md-3">
                    <label for="numero" class="form-label">Número *</label>
                    <input type="text" id="numero" name="numero" class="form-control form-control-sm input-numbers-only" required>
                </div>
                
                <div class="col-md-6">
                    <label for="bairro" class="form-label">Bairro</label>
                    <input type="text" id="bairro" name="bairro" class="form-control form-control-sm input-letters-only">
                </div>
                
                <div class="col-md-6">
                    <label for="complemento" class="form-label">Complemento</label>
                    <input type="text" id="complemento" name="complemento" class="form-control form-control-sm">
                </div>

                <hr class="mt-2">
                <h5 class="mb-2">Outros Dados</h5>

                <div class="col-md-4">
                    <label for="data_nascimento" class="form-label">Data de Nascimento</label>
                    <input type="date" id="data_nascimento" name="data_nascimento" class="form-control form-control-sm">
                </div>
                <div class="col-md-4">
                    <label for="sexo" class="form-label">Sexo</label>
                    <select id="sexo" name="sexo" class="form-select form-select-sm">
                        <option value="">Selecione...</option>
                        <option value="M">Masculino</option>
                        <option value="F">Feminino</option>
                        <option value="Outro">Outro</option>
                    </select>
                </div>
                
                <div class="col-12 mt-3">
                    <button type="submit" class="btn btn-success btn-sm" id="btn-cadastrar-cliente">
                        <i class="fas fa-save me-2"></i> Cadastrar Cliente
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>

<script>
    document.addEventListener('DOMContentLoaded', function() {
        const cepInput = document.getElementById('cep');
        const ruaInput = document.getElementById('rua');
        const bairroInput = document.getElementById('bairro');
        const ufInput = document.getElementById('uf');
        const numeroInput = document.getElementById('numero');
        const cpfInput = document.getElementById('cpf');
        const cpfFeedback = document.getElementById('cpf-feedback');
        const cepFeedback = document.getElementById('cep-feedback');
        const form = document.getElementById('form-cadastro-cliente');

        // --- 1. Validação de CPF (Algoritmo de Luhn) ---
        
        // Função de Validação do CPF (Retorna true ou false)
        function validarCPF(cpf) {
            cpf = cpf.replace(/[^\d]/g, ''); // Remove caracteres não numéricos
            if (cpf.length !== 11 || /^(\d)\1{10}$/.test(cpf)) return false; // 11 dígitos e evita sequências repetidas

            let soma = 0;
            let resto;

            // Validação do 1º dígito verificador
            for (let i = 1; i <= 9; i++) {
                soma += parseInt(cpf.substring(i - 1, i)) * (11 - i);
            }
            resto = (soma * 10) % 11;
            if ((resto === 10) || (resto === 11)) resto = 0;
            if (resto !== parseInt(cpf.substring(9, 10))) return false;

            soma = 0;
            // Validação do 2º dígito verificador
            for (let i = 1; i <= 10; i++) {
                soma += parseInt(cpf.substring(i - 1, i)) * (12 - i);
            }
            resto = (soma * 10) % 11;
            if ((resto === 10) || (resto === 11)) resto = 0;
            if (resto !== parseInt(cpf.substring(10, 11))) return false;

            return true;
        }

        function checkCpfValidity() {
            const cpfValue = cpfInput.value;
            const isCpfValid = validarCPF(cpfValue);

            if (cpfValue.length === 14) { // Só valida se estiver completo (máscara)
                if (isCpfValid) {
                    cpfInput.classList.remove('is-invalid');
                    cpfInput.classList.add('is-valid');
                } else {
                    cpfInput.classList.remove('is-valid');
                    cpfInput.classList.add('is-invalid');
                }
            } else {
                 cpfInput.classList.remove('is-valid', 'is-invalid');
            }
            return isCpfValid;
        }

        cpfInput.addEventListener('keyup', checkCpfValidity);
        cpfInput.addEventListener('change', checkCpfValidity);


        // --- 2. API de Busca de CEP (ViaCEP) ---

        function clearAddressFields() {
            ruaInput.value = "";
            bairroInput.value = "";
            ufInput.value = "";
        }

        function fillAddressFields(data) {
            ruaInput.value = data.logradouro;
            bairroInput.value = data.bairro;
            ufInput.value = data.uf;
            numeroInput.focus(); // Foca no campo de número após preencher
        }

        function searchCep() {
            const cepValue = cepInput.value.replace(/\D/g, ''); // Remove formatação

            if (cepValue.length !== 8) {
                clearAddressFields();
                cepInput.classList.remove('is-valid', 'is-invalid');
                return;
            }

            // Exibe indicador de carregamento (opcional, mas recomendado)
            cepInput.classList.remove('is-valid', 'is-invalid');
            cepInput.disabled = true;

            // Limpa campos para evitar dados antigos
            clearAddressFields();

            fetch(https://viacep.com.br/ws/${cepValue}/json/)
                .then(response => response.json())
                .then(data => {
                    if (!data.erro) {
                        fillAddressFields(data);
                        cepInput.classList.add('is-valid');
                        cepInput.classList.remove('is-invalid');
                    } else {
                        // CEP não encontrado
                        cepInput.classList.add('is-invalid');
                        cepFeedback.textContent = "CEP não encontrado. Digite o endereço manualmente.";
                    }
                })
                .catch(error => {
                    console.error('Erro na API ViaCEP:', error);
                    cepInput.classList.add('is-invalid');
                    cepFeedback.textContent = "Erro ao buscar CEP. Tente novamente.";
                })
                .finally(() => {
                    cepInput.disabled = false;
                });
        }

        // Adiciona o evento de busca ao perder o foco (blur) no campo CEP
        cepInput.addEventListener('blur', searchCep);
        
        // --- 3. Prevenção de envio se o CPF for inválido ---
        
        form.addEventListener('submit', function(e) {
            if (!validarCPF(cpfInput.value)) {
                e.preventDefault();
                cpfInput.classList.add('is-invalid');
                alert('Por favor, corrija o CPF. Ele é obrigatório e precisa ser válido.');
                cpfInput.focus();
            }
            // A validação do CEP é menos crítica, mas a API já ajuda a guiar o usuário
        });
        
        // No caso de dados preenchidos previamente (ex: erro de server-side), verifica o CPF
        if(cpfInput.value) {
            checkCpfValidity();
        }
    });

    // Função de máscara (pressupõe que você já tenha implementado ou vai usar uma biblioteca externa)
    // Exemplo simplificado (você pode precisar de um arquivo de script de máscara mais robusto):
    document.querySelectorAll('.mask-cpf').forEach(input => {
        input.addEventListener('input', function(e) {
            let value = e.target.value.replace(/\D/g, '');
            if (value.length > 9) {
                value = value.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
            } else if (value.length > 6) {
                value = value.replace(/(\d{3})(\d{3})(\d{3})/, '$1.$2.$3');
            } else if (value.length > 3) {
                value = value.replace(/(\d{3})(\d{3})/, '$1.$2');
            } else if (value.length > 0) {
                value = value.replace(/(\d{3})/, '$1');
            }
            e.target.value = value;
        });
    });

    document.querySelectorAll('.mask-cep').forEach(input => {
        input.addEventListener('input', function(e) {
            let value = e.target.value.replace(/\D/g, '');
            if (value.length > 5) {
                value = value.replace(/(\d{5})(\d{1,3})/, '$1-$2');
            }
            e.target.value = value;
        });
    });

</script>