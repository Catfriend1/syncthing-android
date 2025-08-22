function Format-Size() {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true, ValueFromPipeline = $true)]
        [double]$SizeInBytes
    )
    switch ([math]::Max($SizeInBytes, 0)) {
        {$_ -ge 1PB} {"{0:N2} PB" -f ($SizeInBytes / 1PB); break}
        {$_ -ge 1TB} {"{0:N2} TB" -f ($SizeInBytes / 1TB); break}
        {$_ -ge 1GB} {"{0:N2} GB" -f ($SizeInBytes / 1GB); break}
        {$_ -ge 1MB} {"{0:N2} MB" -f ($SizeInBytes / 1MB); break}
        {$_ -ge 1KB} {"{0:N2} KB" -f ($SizeInBytes / 1KB); break}
        default {"$SizeInBytes Bytes"}
    }
}

$hostname = "localhost"
$port = "8384"
$apikey = "YOURAPIKEY"

# Query REST API
$headers = @{ "X-API-Key" = $apikey }
$result = ""
$result = Invoke-WebRequest -uri "http://${hostname}:${port}/rest/system/status" -Headers $headers

# Display raw result
$jsonObject = $result.content | ConvertFrom-Json
# $jsonObject

# Display RAM usage
$jsonObject.sys | Format-Size
# Example output: 74,52 MB

#
# Query Web UI
#
# $user = "USERNAME"
# $pass = "PASSWORD"
# 
# Encode the string to the RFC2045-MIME variant of Base64
# $pair = "${user}:${pass}"
# $bytes = [System.Text.Encoding]::ASCII.GetBytes($pair)
# $base64 = [System.Convert]::ToBase64String($bytes)
# $basicAuthValue = "Basic $base64"
# $headers = @{ Authorization = $basicAuthValue }
# Invoke-WebRequest -uri "http://${hostname}:${port}/" -Headers $headers
