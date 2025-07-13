#
while ($true) {
	$stringToDelete = Read-Host "Please enter string to remove containing rows in xml files"
	$basePath = ($PSScriptRoot + "\..\app\src\main\res")
	#
	Get-ChildItem -Path $basePath -Recurse -Filter strings.xml | ForEach-Object {
		$file = $_.FullName
		$lines = Get-Content $file
		$filteredLines = $lines | Where-Object { $_ -notmatch [regex]::Escape($stringToDelete) }
		if ($lines.Count -ne $filteredLines.Count) {
			Set-Content -Path $file -Value $filteredLines
			Write-Host "Processed: $file"
		}
	}
}
#
Exit 0
