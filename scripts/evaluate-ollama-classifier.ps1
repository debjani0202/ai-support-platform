$ErrorActionPreference = "Stop"

$BaseUrl = "http://localhost:11434"
$Model = "qwen3:4b"
$Endpoint = "$BaseUrl/api/chat"

$Categories = @(
    "ACCOUNT_ACCESS",
    "PAYMENT",
    "TECHNICAL_ISSUE",
    "BUG_REPORT",
    "FEATURE_REQUEST",
    "GENERAL_QUERY"
)

$TestCases = @(
    @{ Title = "Incorrect invoice total"; Description = "The application calculates my invoice total as 1200, but the correct total should be 1000."; Expected = "BUG_REPORT" },
    @{ Title = "Payment deducted twice"; Description = "My bank account was charged twice for the same transaction."; Expected = "PAYMENT" },
    @{ Title = "Unable to reset password"; Description = "I cannot reset my password or access my account."; Expected = "ACCOUNT_ACCESS" },
    @{ Title = "Slow dashboard"; Description = "The dashboard takes 40 seconds to load, but the displayed data is correct."; Expected = "TECHNICAL_ISSUE" },
    @{ Title = "Add dark mode"; Description = "I would like a dark theme option for the dashboard."; Expected = "FEATURE_REQUEST" },
    @{ Title = "Download monthly report"; Description = "How can I download my monthly report?"; Expected = "GENERAL_QUERY" },
    @{ Title = "Application crashes"; Description = "The application closes unexpectedly when I open reports."; Expected = "BUG_REPORT" },
    @{ Title = "Refund not received"; Description = "My payment was refunded, but I have not received the money."; Expected = "PAYMENT" },
    @{ Title = "Connection problem"; Description = "The application cannot connect to the server."; Expected = "TECHNICAL_ISSUE" },
    @{ Title = "Change profile picture"; Description = "How can I update the picture on my profile?"; Expected = "GENERAL_QUERY" }
)

$PromptRules = @(
    "You classify customer support tickets. Choose exactly one category based on the main problem described."
    "ACCOUNT_ACCESS: Login failures, password problems, account lockouts, or authentication issues."
    "PAYMENT: Payment transactions, duplicate charges, failed payments, refunds, or payment processing problems."
    "TECHNICAL_ISSUE: General technical difficulties, slow performance, or connectivity problems without a specific software defect."
    "BUG_REPORT: The application crashes, calculates an incorrect result, displays incorrect data, or behaves unexpectedly."
    "FEATURE_REQUEST: Requests for new functionality that does not currently exist, or explicit improvements to existing functionality."
    "GENERAL_QUERY: General questions, including how to use or access existing functionality."
    "If the application crashes or produces an incorrect calculation or result, choose BUG_REPORT."
    "If a payment transaction is duplicated, fails, or is processed incorrectly, choose PAYMENT."
    "Do not choose PAYMENT merely because a ticket mentions an invoice, bill, or amount. If the software calculates the amount incorrectly, choose BUG_REPORT."
    "If the application is slow or cannot connect, choose TECHNICAL_ISSUE unless a specific software defect is described."
    "Choose ACCOUNT_ACCESS only when the main problem concerns login, authentication, passwords, or account access."
    "If the user asks how to use or change an existing feature, choose GENERAL_QUERY."
    "If the user asks to add functionality that does not exist, choose FEATURE_REQUEST."
    "Classify the underlying problem, not just individual keywords."
    "Return only the category name in the required JSON format."
) -join "`n"
try {
    $null = Invoke-RestMethod -Uri "$BaseUrl/api/tags" -TimeoutSec 5
}
catch {
    Write-Host "Cannot connect to Ollama at $BaseUrl. Start Ollama and retry." -ForegroundColor Red
    exit 1
}

$Results = @()
$Correct = 0

for ($i = 0; $i -lt $TestCases.Count; $i++) {
    $case = $TestCases[$i]
    $prompt = "$PromptRules
Title: $($case.Title)
Description: $($case.Description)"

    $body = @{
        model = $Model
        messages = @(@{ role = "user"; content = $prompt })
        stream = $false
        think = $false
        format = @{
            type = "object"
            properties = @{
                category = @{ type = "string"; enum = $Categories }
            }
            required = @("category")
            additionalProperties = $false
        }
        options = @{ temperature = 0; num_predict = 50 }
    } | ConvertTo-Json -Depth 10

    $predicted = "ERROR"
    $errorMessage = ""

    try {
        $response = Invoke-RestMethod -Uri $Endpoint -Method Post -ContentType "application/json" -Body $body -TimeoutSec 120
        $answer = $response.message.content | ConvertFrom-Json
        $predicted = ([string]$answer.category).Trim().ToUpperInvariant()

        if ($predicted -notin $Categories) {
            throw "Invalid category returned: $predicted"
        }
    }
    catch {
        $errorMessage = $_.Exception.Message
    }

    $passed = $predicted -eq $case.Expected
    if ($passed) {
        $Correct++
        $status = "PASS"
    } else {
        $status = "FAIL"
    }

    $Results += [PSCustomObject]@{
        Number = $i + 1
        Title = $case.Title
        Expected = $case.Expected
        Predicted = $predicted
        Result = $status
    }

    Write-Host "[$status] $($case.Title): expected $($case.Expected), got $predicted"
    if ($errorMessage) { Write-Host "Error: $errorMessage" -ForegroundColor Red }
}

$accuracy = [math]::Round(($Correct / $TestCases.Count) * 100, 2)

Write-Host ""
Write-Host "Total: $($TestCases.Count) | Correct: $Correct | Failed: $($TestCases.Count - $Correct) | Accuracy: $accuracy%"
$Results | Format-Table Number, Expected, Predicted, Result -AutoSize

$resultsPath = Join-Path $PSScriptRoot "ollama-evaluation-results.csv"
$Results | Export-Csv -Path $resultsPath -NoTypeInformation -Encoding utf8
Write-Host "Results saved to: $resultsPath"