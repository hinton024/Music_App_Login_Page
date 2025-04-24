import json
import boto3
from boto3.dynamodb.conditions import Attr

dynamodb = boto3.resource('dynamodb')
table = dynamodb.Table('Music')

def lambda_handler(event, context):
    method = event['httpMethod']
    path = event['path']

    if method == 'GET' and path == '/music/search':
        return search_music(event)
    else:
        return {'statusCode': 400, 'body': json.dumps('Invalid Request')}

def search_music(event):
    query_params = event.get('queryStringParameters', {}) or {}
    title = query_params.get('title')
    artist = query_params.get('artist')
    year = query_params.get('year')
    album = query_params.get('album')
    
    # Build filter expression for scan
    filter_expression = None
    
    if title:
        filter_expression = Attr('title').contains(title)
    
    if artist:
        artist_condition = Attr('artist').contains(artist)
        filter_expression = artist_condition if filter_expression is None else filter_expression & artist_condition
    
    if year:
        # Handle year as both string and number
        try:
            year_as_number = int(year)
            year_condition = (Attr('year').eq(year) | Attr('year').eq(year_as_number))
            filter_expression = year_condition if filter_expression is None else filter_expression & year_condition
        except ValueError:
            year_condition = Attr('year').eq(year)
            filter_expression = year_condition if filter_expression is None else filter_expression & year_condition
    
    if album:
        album_condition = Attr('album').contains(album)
        filter_expression = album_condition if filter_expression is None else filter_expression & album_condition
    
    # Execute the scan with filter expression if one exists
    if filter_expression:
        response = table.scan(FilterExpression=filter_expression)
    else:
        response = table.scan()
    
    items = response.get('Items', [])
    
    # Process response for client
    if not items:
        return {
            'statusCode': 200,
            'body': json.dumps({
                'success': False,
                'message': 'No result is retrieved. Please query again'
            })
        }
    
    # Format results for client
    results = []
    for item in items:
        music_item = {
            'id': item.get('id'),
            'title': item.get('title'),
            'artist': item.get('artist'),
            'year': str(item.get('year', '')),
            'album': item.get('album', '')
        }
        results.append(music_item)
    
    return {
        'statusCode': 200,
        'body': json.dumps({
            'success': True,
            'results': results
        })
    }
