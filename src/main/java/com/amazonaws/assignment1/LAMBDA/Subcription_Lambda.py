import json
import boto3
import uuid

dynamodb = boto3.resource('dynamodb')
table = dynamodb.Table('Subscription')  # Changed from 'SubscriptionsTable' to 'Subscription'

def lambda_handler(event, context):
    method = event['httpMethod']
    path = event['path']

    if method == 'POST' and path == '/subscriptions':
        return add_subscription(event)
    elif method == 'DELETE' and path.startswith('/subscriptions/'):
        return remove_subscription(event)
    elif method == 'GET' and path.startswith('/subscriptions/'):
        return get_subscriptions(event)
    else:
        return {'statusCode': 400, 'body': json.dumps('Invalid Request')}

def add_subscription(event):
    body = json.loads(event['body'])
    userEmail = body['userEmail']  # Changed from user_id to userEmail
    musicId = body['musicId']      # Changed from song_id to musicId
    title = body.get('title')
    artist = body.get('artist')
    year = body.get('year', '')
    album = body.get('album', '')
    
    # Generate a unique ID for the subscription
    subscription_id = str(uuid.uuid4())

    item = {
        'id': subscription_id,         # Primary key is now 'id'
        'userEmail': userEmail,        # Secondary attribute for queries
        'musicId': musicId,
        'title': title,
        'artist': artist,
        'timestamp': int(round(boto3.Session().client('dynamodb').get_time().timestamp()*1000))
    }
    
    # Add optional fields if present
    if year:
        item['year'] = year
    if album:
        item['album'] = album

    table.put_item(Item=item)
    return {'statusCode': 200, 'body': json.dumps({
        'success': True,
        'message': 'Subscribed successfully',
        'subscription': {
            'id': subscription_id,
            'musicId': musicId,
            'title': title,
            'artist': artist
        }
    })}

def remove_subscription(event):
    # Extract subscription ID directly from path parameter
    subscription_id = event['pathParameters']['id']

    # Get the subscription to verify user ownership
    response = table.get_item(Key={'id': subscription_id})
    item = response.get('Item')
    
    if not item:
        return {'statusCode': 404, 'body': json.dumps({
            'success': False,
            'message': 'Subscription not found'
        })}
    
    # Verify user email from session matches subscription owner
    # (This would require session info to be passed in the event)
    userEmail = event.get('userEmail') or event.get('queryStringParameters', {}).get('userEmail')
    if userEmail and userEmail != item['userEmail']:
        return {'statusCode': 403, 'body': json.dumps({
            'success': False,
            'message': "You don't have permission to remove this subscription"
        })}

    # Delete the subscription
    table.delete_item(Key={'id': subscription_id})
    
    return {'statusCode': 200, 'body': json.dumps({
        'success': True,
        'message': 'Subscription removed successfully'
    })}

def get_subscriptions(event):
    userEmail = event['queryStringParameters']['userEmail']  # Changed from user_id to userEmail

    # Use a query with a filter expression
    response = table.scan(
        FilterExpression=boto3.dynamodb.conditions.Key('userEmail').eq(userEmail)
    )

    return {
        'statusCode': 200,
        'body': json.dumps({
            'success': True,
            'subscriptions': response['Items']
        })
    }
