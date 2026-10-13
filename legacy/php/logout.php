<?php
// 1. Inicia a sessão

session_start();


$_SESSION = array();


session_destroy();


header("Location: login.php");


exit;
?>